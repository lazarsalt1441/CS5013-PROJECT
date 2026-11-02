# Structured Debugging Practice (v3)

The method is unchanged from [v2](../docs_v2/DEBUGGING.md): reproduce, observe, hypothesize, isolate, correct, test, regress, document. v3 adds a third implementation boundary, since the same prerequisite rule now exists in JavaScript and Java. It also adds a database whose behaviour differs between the test double and production.

## Tools added in v3

- **Golden file across languages.** If the portal and the CLI disagree about a warning, run both suites. Whichever fails `expected_warnings.csv` owns the fault.
- **Two database targets.** `npm test` uses pg-mem (fast, no network); `npm run test:integration` uses real Postgres. A test that passes on one and fails on the other points at a database feature, not application logic.
- **Local end to end.** `npm run dev` runs the real handlers against any `DATABASE_URL`, so a form bug can be reproduced without Vercel.
- **Generic 500s.** Clients see "Unexpected server error."; the full error is in the server log (`vercel logs`, or the `npm run dev` terminal).

## Faults found while building v3

### 1. Published results were never cached (present since v2)

- **Reproduce:** The new test "published results are served publicly with a short cache" failed.
- **Observe:** `Cache-Control` was `no-store`, although `results.js` sets `public, max-age=30`.
- **Hypothesize:** Something set the header after the handler did.
- **Isolate:** `json()` in `_lib.js` unconditionally set `no-store` before writing the body. v2 had the same order, so the cache policy had never been applied.
- **Correct:** `json()` only sets `no-store` when no `Cache-Control` is present.
- **Test:** The same test now passes; the test response recorder gained `getHeader` to match Node's API.

### 2. A transaction test passed when it should have failed

- **Reproduce:** In pg-mem, `BEGIN; INSERT; ROLLBACK;` left the row in place.
- **Observe:** pg-mem accepts `ROLLBACK` but does not undo the work.
- **Hypothesize:** Rollback-dependent guarantees cannot be tested on pg-mem at all.
- **Correct:** No product change. Rollback and concurrency tests moved to `tests/integration`, which runs on real Postgres 16, locally and as a CI service container. `store.test.mjs` says why it has no rollback test.
- **Lesson:** A test double's limits are part of the test design. Here a whole category of guarantee would otherwise have gone untested.

### 3. `CREATE TABLE IF NOT EXISTS` failed in pg-mem only

- **Observe:** The second `migrate()` run threw "Not supported" from pg-mem's planner.
- **Correct:** `migrate()` checks `information_schema.tables` first, which works on both pg-mem and Postgres. A comment explains why.

### 4. U and W grades counted as completed in the portal's snapshot check

- **Reproduce:** The new cross-language parity test returned 18 warnings where Java returned 22.
- **Observe:** All four missing warnings were `MISSING_PREREQUISITE` for S002 and S005, whose only EE3110 rows have grades U and W.
- **Hypothesize:** The JS snapshot path trusted every stored row as completed.
- **Isolate:** `warningsForSnapshot` added every completion row to the completed set. Live submissions were unaffected, because grade-card parsing already drops U/W/I before storing. Any other source (the planned admin records, a CSV import) would have produced wrong answers.
- **Correct:** Both JS call sites now apply `isCompleted`, the rule the parser uses, wherever completions are read.
- **Test:** `parity.test.mjs` (golden file) and "U and W rows from a CSV import do not count as completed".

### 5. An optional CSV column defaulted to `null`

- **Reproduce:** `PrerequisiteLoaderTest.termAndSourceColumnsAreOptional` failed with `source=null`.
- **Observe:** `row.getOrDefault("source", "grade_card").isBlank() ? "grade_card" : row.get("source")`.
- **Isolate:** When the column was absent, the default made the condition false, and the code then re-read the absent column.
- **Correct:** Read the value once into a local variable and test that.
- **Test:** The same test passes; the bug would otherwise have shown up as a `null` source in exports.

### 6. The fixture guard passed for the wrong reason

- **Reproduce:** After adding the "no real grade cards" check, the synthetic fixture passed, and a real card temporarily copied in failed.
- **Observe:** The synthetic PDF's text is compressed, so searching the raw bytes for `SYNTHETIC` found nothing. It passed only because no text operators were visible. A compressed real card would also have passed.
- **Correct:** `validate.mjs` extracts the PDF text with the same library the parser uses, then requires the marker.
- **Test:** Re-ran with the real card copied in: rejected. Removed it again: passes.

### 7. Maven could not reach Maven Central in the build sandbox

- **Observe:** First `UnknownHost`. With a proxy configured, 403 from the egress proxy for `repo.maven.apache.org` and `repo1.maven.org`.
- **Isolate:** A network policy of the AI build sandbox, not a project fault. Node packages and PostgreSQL were reachable.
- **Response:** No workaround around the policy. The new Java classes and their 19 JUnit tests were compiled with `javac` and run against minimal JUnit-API stand-ins kept outside the repository. The full Maven suite is left to CI, and [VERIFICATION.md](VERIFICATION.md) records exactly which evidence came from where.

### 8. Every PDF upload failed on Node 20, and the error blamed the file

- **Reproduce:** The first CI run on PR #1 failed 5 portal tests, all of which read a real PDF. Locally on Node 22 all of them passed.
- **Observe:** Each failure said "The grade card could not be read as a PDF". The synthetic fixture is a valid PDF.
- **Hypothesize:** The fixture was fine, and the error message was hiding the real exception. The difference was the Node version (CI 20, local 22).
- **Isolate:** On Node 20.20, calling unpdf directly gave the real error, "Serverless PDF.js bundle could not be resolved: ReferenceError: Iterator is not defined". unpdf declares `engines: node >=22`, which npm reports only as a warning.
- **Correct:**
  - Require Node 22 in `portal/package.json` (Vercel selects the runtime from it) and in CI.
  - In `_gradecard.js`, translate only pdf.js file errors (`InvalidPDFException`, `PasswordException` and similar) into a student-facing message, and rethrow anything else, so it becomes a logged 500.
- **Test:** "a PDF library fault is not reported to the student as a bad file" simulates a library failure and checks that it is not turned into a `GradeCardError`. The suite is now 56/56.
- **Lesson:** A catch-all that converts every exception into a friendly message turns an outage into a misleading user error. Catch only the errors you mean to translate.
