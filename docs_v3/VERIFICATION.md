# Verification Record (v3)

Date: 2 November 2026. Prompt: P015.

This record separates what was **run** from what is **left to CI**. The AI build sandbox could reach npm and run PostgreSQL, but not Maven Central (blocked by its network policy; see [DEBUGGING.md §7](DEBUGGING.md#7-maven-could-not-reach-maven-central-in-the-build-sandbox)).

## Environment

- Node.js 22.23 (CI: Node 20); `pg` 8, `pg-mem` 3, `unpdf` 1
- PostgreSQL 16.15 (local, throwaway cluster; CI: `postgres:16` service)
- OpenJDK 21.0.12, compiling with `--release 17`; Maven 3.9.11 (could not download dependencies)
- Google Chrome (headless, Playwright) for the browser check
- groff for the man page

## Portal: run here

```text
npm ci                         found 0 vulnerabilities
npm test                       tests 55  pass 55  fail 0
npm run test:integration       tests 4   pass 4   fail 0   (TEST_DATABASE_URL -> PostgreSQL 16.15)
npm run test:integration       tests 4   skipped 4         (without TEST_DATABASE_URL, as designed)
npm run check                  Portal validation passed: 18 courses, 17 slots, 30 required files.
npm run db:migrate             applied 001_initial_schema.sql, 002_prerequisites.sql; second run: already up to date
```

The fixture guard was tested both ways: with a real grade card temporarily copied into `tests/fixtures/`, `npm run check` failed with the `SYNTHETIC`-marker message, and passed again after it was removed.

## Browser: run here

`npm run dev` against PostgreSQL, driven by headless Chrome:

| Step | Observed |
|---|---|
| Professor: EE5141, prerequisites `EE1101, EE2001`, slot A preferred | "2 prerequisite(s) recorded for EE5141." |
| Student `XX00Z999`: ranks EE5141, uploads `synthetic_grade_card.pdf` | Saved, with a warning: "EE5141 lists EE2001 as a prerequisite, which is not on your completed courses." (EE2001 is W on the card; EE1101 was retaken and passed) |
| Admin dashboard | 2 rules, 1 warning, row shown |
| Console | No JavaScript errors |

## Grade-card parser on a real card: run here, not committed

A real IITM grade card supplied by the developer was parsed locally: 43 course rows read, the same count `pdftotext -layout` shows, with no unreadable rows and the roll number found. The file stayed in a scratch directory and is not in Git; no names, roll numbers or grades from it appear in the repository.

## Java: run here with stand-ins

Maven could not download cpsolver, OR-Tools, JavaFX or JUnit. To still execute the new code:

- `javac --release 17` compiled every new and changed class that does not need those libraries: `model`, `io`, `validation.PrerequisiteChecker`, `service`, `cli.SlotbookCli`, and the 3 new test classes. The only lint warning is in unchanged v2 code (`DatasetLoader.RowParseException` lacks a `serialVersionUID`).
- The real `SolverChoice` constructs the solvers, so it was replaced by a stub with the same API. None of the new tests call it; the service tests use a recording solver.
- Minimal JUnit-API stand-ins (`@Test`, `@TempDir`, `Assertions`) and a reflection runner executed the tests:

```text
passed 19, failed 0
  PrerequisiteCheckerTest          8/8
  PrerequisiteLoaderTest           6/6
  PrerequisiteWarningsDatasetTest  5/5
```

The stand-ins live outside the repository and are not part of the build.

- `SlotbookCli --help` printed the new options and exit-status line. `--bogus` was rejected as an unknown option, and `--prerequisites nope.csv` as a missing file.

## Java: left to CI

| What | Expected |
|---|---|
| `mvn clean test` | `Tests run: 71, Failures: 0, Errors: 0`: v2's 52 plus 19 new |
| JavaFX compilation | `ui/SlotbookApplication` unchanged; it uses `SlotbookService.run(Path)` and `SlotbookRun` accessors, whose signatures are unchanged |
| CLI on `data/prerequisite-warnings` | Prints `Prerequisite warnings: 22`; `prerequisite_warnings.csv` equals `expected_warnings.csv` (the CI step diffs them) |
| v2 demonstrations | Unchanged results; `data/feasible` objective 20 |

Update this section with the CI run link and result once the branch is pushed.

## Man page: run here

```text
groff -man -ww -z man/slotbook.1                     no warnings
groff ... | diff - man/slotbook.1.txt                identical
```
