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

### 9. The browser never checked the student ID (present since v2)

- **Reproduce:** A full walkthrough in headless Chrome that also captured `console` errors, not only uncaught page errors.
- **Observe:** "Pattern attribute value `[A-Za-z0-9_-]{3,30}` is not a valid regular expression … Invalid character in character class", plus a 404 on every page.
- **Isolate:** Browsers compile `pattern=""` with the RegExp `v` flag, which rejects an unescaped `-` at the end of a class. An invalid pattern is ignored, so the in-form check never ran. The server-side validator still rejected bad IDs, so no bad data was stored. The 404 was `/favicon.ico`: no page declared an icon.
- **Correct:** Escape the hyphen (`[A-Za-z0-9_\-]`) and add an inline SVG favicon to every page.
- **Test:** `npm run check` now compiles every `pattern` attribute with the `v` flag and requires a favicon link. Run against the old page, it reports both faults. The walkthrough now shows no console errors, and the browser rejects `a!` while accepting `S-031_a`.
- **Lesson:** The earlier smoke test listened only for uncaught exceptions; browser-reported problems arrive as console errors. Listen to both.

## Faults found by the stress suite (P017)

### 10. A corrupted grade card caused a server error

- **Reproduce:** `fuzz.stress.mjs`, 300 mutated PDFs. Mutation 0 (junk appended) escaped as `UnknownErrorException: Invalid number: %`.
- **Isolate:** The fix in §8 translated a fixed list of pdf.js error names into a student-facing message. `UnknownErrorException` wasn't on the list, so the upload became a 500. Errors from `getPage` and `getTextContent` were outside the `try` entirely.
- **Correct:** Every read step is inside the `try`, and errors are classified by origin: pdf.js's own `*Exception` classes and `FormatError` mean a bad file; plain JavaScript errors mean a server fault. §8's guarantee still holds, and its regression test passes.
- **Test:** The fuzz test, on four seeds (about 210 of 300 rejected cleanly, the rest parsed), plus a unit test of the classifier.
- **Lesson:** An allow-list of library error names is fragile; classify by what produced the error.

### 11. Grade-card row building was quadratic

- **Reproduce:** `volume.stress.mjs`. 10x the text items took 102x the time; 20,000 rows took about 1 s.
- **Isolate:** `itemsToRows` searched all rows built so far for every item (`rows.find`).
- **Correct:** The items are already sorted top to bottom, so an item can only join the row currently being built. One pass is enough: 20,000 rows now take 24 ms.
- **Test:** The equal-work scaling check measures 1.7–2.0 for the new code and 9.3 for the old (limit 5).
- **Lesson:** Uploads up to 3 MB are attacker-sized inputs. Anything that runs per text item must be linear.

### 12. Concurrent prerequisite updates for one course returned 500

- **Reproduce:** `postgres.stress.mjs`. 17 of 25 professors replacing EE1000's prerequisites at once got 500, with `duplicate key value violates unique constraint "prerequisites_pkey"` in the log.
- **Isolate:** Under READ COMMITTED, each transaction deletes the course's rows, then inserts its own. Two transactions can't see each other's uncommitted inserts, so both insert the same key, and the later commit fails. Different professors don't share a row lock, unlike two submissions by the same student or professor.
- **Correct:** `saveInstructor` takes `pg_advisory_xact_lock(hashtext('prerequisites:' || course))` before the delete. Writers for the same course take turns, the last writer wins, and the lock is released on commit or rollback.
- **Test:** The stress test passes on three seeds and fails when the lock line is removed. pg-mem lacks advisory locks, so `tests/helpers.mjs` registers no-op stand-ins; pg-mem runs one query at a time, so nothing is lost.
- **Lesson:** The v3 integration test for concurrency used `Promise.allSettled` and only checked the final state, so failed writes passed silently. A stress test must also require every request to succeed.

### Test-design corrections (not product faults)

- The first instructor fuzz run asserted that more than 200 of 5,000 random payloads would be valid; the generator produces about 160. The threshold exists only to make sure the invariants get exercised, so it is now 100.
- The first 20,000-row grade-card test generated course codes like `EE20999`. The parser correctly rejects 5-digit codes, so the test data now stays within `EE1000`–`EE9999`.
- The first scaling checks compared one run on 10x input with one small run (median of three). For the same linear Java code they measured anywhere from 8x to 24x, because the small run took only tens of milliseconds. They now compare equal total work, after warm-up, fastest of five.

## Faults found by the course quality tools (P019)

### 13. Touching meetings at off-grid minutes were reported as a clash

- **Reproduce:** jqwik, `SlotPropertiesTest.overlapMatchesTheIntervalDefinition`, first run, shrunk to one meeting each: `07:01` for 20 min and `07:21` for 10 min on the same day.
- **Observe:** `Slot.overlaps` returned true; the oracle (same day and `x.start < y.end && y.start < x.end`) said false.
- **Isolate:** `Slot.cells()` maps a meeting to 5-minute cells, rounding the start down and the end up. Off the grid, two meetings that only touch share a cell.
- **Correct:** UniTime's week code also uses 5-minute cells, so moving to 1-minute cells would make the two solvers disagree. Instead `Slot.meetings()` rejects a start or length that is not a multiple of 5, and `InputValidator` reports it as `INVALID_SLOT_TIMES` before any solver runs. Every slot in `data/` was checked and is on the grid.
- **Test:** The property now generates on-grid times (3,000 tries); `timesOffTheFiveMinuteGridAreRejected` covers the rest; `reportsSlotTimesOffTheFiveMinuteGrid` checks the validator.
- **Lesson:** Hand-written examples all used round times, so the rounding was never exercised. A generator does not share the author's habits.

### 14. Day and room names depended on the machine's locale

- **Reproduce:** SpotBugs (`-Pquality`, effort Max) reported 10 findings; 3 were `DM_CONVERT_CASE`, in `Slot` (day names), `OrToolsTimetableSolver` and `UniTimeModelBuilder` (room keys).
- **Isolate:** `toUpperCase()` with no locale uses the JVM default. Under `tr-TR`, `"wed".toUpperCase()` is fine but `"fri"` becomes `"FRİ"` (dotted capital I), so the day is unknown and the slot is rejected; room keys with `i` stop matching.
- **Correct:** `toUpperCase(Locale.ROOT)` in all three places, the smallest patch.
- **Test:** `SlotAndTimeGridTest.dayNamesParseTheSameInEveryLocale` sets the default to `tr-TR`; it fails on the old code.
- **Other findings:** 2 exposed-representation warnings on `UniTimeModelBuilder.Built` were fixed with unmodifiable copies; 1 unread field and 1 dead method in `TimeGrid` were removed; 1 missing `serialVersionUID` was added. Two remain suppressed in `spotbugs-exclude.xml`, each with a reason: the cpsolver `Model` must be shared with the solver, and one cast is generated by `javac`. Result: 0 findings, and CI fails on any new one.

### 15. Mutation testing: gaps and the two equivalent mutants

- **First run:** PIT on `validation`, `model.Slot*`, `model.TimeGrid`, `io.CsvSupport` and `io.PrerequisiteLoader`: 156 mutants, 91.0% killed (8 survived, 6 had no covering test), with JaCoCo line coverage already near 80%. Coverage said the code ran; PIT showed several checks whose result no test looked at.
- **Correct:** New example tests for each surviving behaviour (a duplicate slot hiding later issues, a blank historical slot, overlapping labs in protected pairs and for one instructor, an unknown course in a student's choices, a zero-length meeting, a wrong column count naming its line). Dead code that could not be killed was deleted rather than tested.
- **Result:** 153 mutants, 151 killed (98.7%). The build now fails below 97% (`mutationThreshold` in `pom.xml`).
- **Equivalent mutants (left, not tested):**
  - `InputValidator.validate`, line 111, conditional boundary: the loop stops one element later, but the extra element is the pair `(i, i)`, which the body skips. No input can tell the two apart.
  - `Slot.cells`, line 72, multiplication replaced by division: the value is only the initial size hint for a `BitSet`, which grows on demand. Behaviour is unchanged.
- **Lesson:** Coverage measures what ran; mutation score measures what the tests would notice.
