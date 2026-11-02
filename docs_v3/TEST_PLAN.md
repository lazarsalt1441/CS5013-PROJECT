# Test Plan (v3)

Every automated test in the repository is listed below. **New in v3** marks tests added under P015.

| Suite | Command | Tests | Needs |
|---|---|---|---|
| Java engine | `mvn clean test` | 71 (47 methods, plus 10 parameterised cases and 14 generated scenarios) | JDK 17+, Maven |
| Portal unit and contract | `cd portal && npm test` | 55 | Node 20+; no network, no database |
| Portal integration | `cd portal && TEST_DATABASE_URL=... npm run test:integration` | 4 | A throwaway Postgres (CI uses `postgres:16`); all skip without the variable |
| Portal static checks | `cd portal && npm run check` | 1 script, many assertions | Node |
| Man page | `groff -man -ww -z man/slotbook.1` and a diff against `slotbook.1.txt` | 1 CI step | groff |
| CLI demonstrations | CI `test` job | 5 runs | Maven |

GitHub Actions runs all of these on every push and pull request (`.github/workflows/ci.yml`).

## Shared contract between the two implementations

`data/prerequisite-warnings/expected_warnings.csv` is a golden file: 22 warnings for the `data/prerequisite-warnings` dataset.

- Java `PrerequisiteWarningsDatasetTest.javaWarningsEqualTheSharedGoldenFile` asserts `PrerequisiteChecker` produces exactly these rows.
- Portal `parity.test.mjs` asserts `warningsForSnapshot` produces exactly these rows.
- CI runs the real CLI on the dataset and diffs its `prerequisite_warnings.csv` against the same file.

If either implementation changes the rule, a code or a message, one of the three fails.

## Java engine (`mvn clean test`)

### `io` package

| Class | Test | Verifies |
|---|---|---|
| `CsvSupportTest` | `parsesQuotedCommasAndEscapedQuotes` | Quoted commas and doubled quotes parse |
| `DatasetLoaderTest` | `loadsAllFiveFixtureFiles` | 18 courses, 17 slots, 11 conflicts, 20 instructor and 90 student rows |
| | `derivesPositiveCoDemandWeights` | Shared selections produce positive pair weights |
| `SlotbookExporterTest` | `exportsAssignmentsAndExplanations` | Both CSV exports exist with assignment and explanation evidence |
| `PrerequisiteLoaderTest` **(new in v3)** | `readsBothFilesAndNormalisesCase` | Both optional files load; IDs and grades are upper-cased |
| | `termAndSourceColumnsAreOptional` | Missing `term` gives "", missing `source` gives `grade_card` (regression for a null default) |
| | `absentOptionalFilesMeanNoInputs` | No files means empty data, not an error |
| | `explicitPathsOverrideTheDatasetDirectory` | `--prerequisites` beats `DIR/prerequisites.csv` |
| | `aMissingRequiredValueIsAnError` | A blank `prereq_course_id` is rejected with the column name |
| | `aNamedFileThatDoesNotExistIsAnError` | An explicitly named missing file fails |
| `PrerequisiteWarningsDatasetTest` **(new in v3)** | `javaWarningsEqualTheSharedGoldenFile` | Checker output equals `expected_warnings.csv` row for row |
| | `solverFilesAreIdenticalToTheFeasibleDataset` | The new dataset's five solver files load to the same problem as `data/feasible` |
| | `prerequisiteInputsNeverChangeWhatTheSolverReceives` | A recording solver receives an identical problem with and without prerequisite inputs; 22 warnings when checked |
| | `exporterWritesWarningsOnlyWhenPrerequisitesWereChecked` | `prerequisite_warnings.csv` is written with the export header only when rules were supplied |
| | `theTwoArgumentRunConstructorKeepsTheV2Behaviour` | v2 callers of `new SlotbookRun(problem, solution)` see "not checked" |

### `validation` package

| Class | Test | Verifies |
|---|---|---|
| `InputValidatorTest` | `feasibleFixtureHasNoValidationErrors` | Clean fixture, no issues |
| | `identifiesContradictoryFixedAssignments` | `FIXED_CONFLICT` |
| | `rejectsInconsistentPreferencesLabsAndHistory` | Lab as lecture, unknown historical slot, unknown lab, fixed slot over own lab, duplicate preference, negative penalty |
| `PrerequisiteCheckerTest` **(new in v3)** | `noWarningsWhenEveryPrerequisiteIsCompleted` | Completed prerequisites give no warnings |
| | `reportsEachMissingPrerequisiteInRankThenCourseOrder` | One `MISSING_PREREQUISITE` per gap, ordered by rank then prerequisite; exact message text |
| | `gradesUWAndIDoNotCompleteACourse` | U, W, I (any case) do not complete; S, A–E, P do |
| | `aRetakePassCountsEvenAfterAFail` | A U followed by a C counts as completed |
| | `studentWithoutAnyRecordGetsOneSummaryWarning` | One `NO_COMPLETION_RECORD` listing only constrained courses; exact message |
| | `coursesWithoutRulesAndEmptyRuleSetsNeverWarn` | No rules, no warnings |
| | `aSelfPrerequisiteIsReportedAndIgnored` | `SELF_PREREQUISITE` and the rule is skipped |
| | `studentsAreReportedInOrderOfFirstAppearance` | Stable student order |

### `solver` and `testing` packages (unchanged from v2)

| Class | Test | Verifies |
|---|---|---|
| `OrToolsTimetableSolverTest` | `assignsEveryCourse` | Every course gets a slot |
| | `usesOnlyAllowedSlotsAndHonoursFixedSlots` | Allowed and fixed slots respected |
| | `separatesEveryHardConflict` | Protected pairs never overlap |
| | `emitsOneCompleteExplanationPerCourse` | One explanation per course |
| | `rejectsKnownInfeasibleFixtureBeforeSolving` | Validator stops the infeasible fixture |
| `UniTimeTimetableSolverTest` | `assignsEveryCourseWithoutBreakingHardRules` | `FEASIBLE` with no violations |
| | `separatesTheRoomClashFromVersionOne` | EE1100 and EE1101 no longer share CRC in slot E |
| | `uniTimeObjectiveEqualsTheSlotbookPenalty` | Reported objective equals `PenaltyCalculator` |
| | `matchesTheExactOptimumOnTheFeasibleFixture` | Objective 20, the CP-SAT optimum |
| | `emitsOneCompleteExplanationPerCourse` | One explanation per course |
| | `sameIterationCapGivesSameAnswer` | Deterministic with an iteration cap |
| | `rejectsKnownInfeasibleFixtureBeforeSolving` | Validator stops the infeasible fixture |
| `GeneratedScenarioTest` | `bothSolversMatchIndependentOracles` | 14 dynamic tests, one per generated scenario ([comparison table](../docs_v2/TEST_PLAN.md#solver-comparison)) |
| | `sameScenarioSolvesToSameObjective` | Re-solving is stable |
| `BruteForceOptimalityTest` | `cpSatMatchesExhaustiveOptimum` | 10 parameterised seeds: CP-SAT equals the exhaustive optimum |
| `TestingFrameworkTest` | `penaltyCalculatorMatchesHandComputation` | Penalty oracle |
| | `constraintCheckerFlagsEveryViolationKind` | Checker oracle |
| | `timeGridUsesRealMinutes` | 14:30 overlaps a 14:00–16:45 lab; back-to-back does not |
| | `checkerFlagsOwnLabAndRoomClashes` | Own-lab and room clashes |
| | `studentPenaltyCountsLabOverlaps` | Labs count toward student penalties |
| | `bruteForceFindsHandComputedOptimum` | Brute-force oracle |
| | `generatedScenariosKeepPlantedAssignmentFeasible` | Planted timetable stays feasible |
| | `csvWriterRoundTripsThroughDatasetLoader` | Generated CSVs reload |

Count: 47 `@Test` methods + 10 seeds + 14 scenarios = **71**.

## Portal (`npm test`, 55 tests)

### `tests/validation.test.mjs` (10, new in v3)

Pure input rules, no database.

- student: IDs are upper-cased and ranks follow list order
- student: rejects malformed IDs, missing fields and bad list sizes
- student: rejects duplicate and invalid course IDs
- validation errors carry HTTP status 400
- clean strips control characters and truncates
- instructor: key is case-insensitive and penalties must be 0-100 integers
- instructor: prerequisites are parsed from free text and de-duplicated
- instructor: absent prerequisites mean unchanged, empty means none
- instructor: prerequisites need a course and cannot include the course itself
- publication: requires slotbook rows with unique course IDs

### `tests/store.test.mjs` (8, new in v3)

The SQL layer on an in-memory Postgres (pg-mem); each test gets a fresh database.

- migrations apply in order and are recorded once
- a student resubmission replaces earlier preferences
- completions are replaced only when a new list is supplied
- instructor names are matched case-insensitively
- prerequisites are replaced per course and left alone when absent
- database constraints reject rows the validator would also reject
- public results show only the latest published run
- clearAll empties every table

### `tests/api.test.mjs` (16; rewritten for v3 from the 7 v2 tests)

Each Vercel handler with a fake request and response.

- CSV export preserves the Java parser contract and quotes unsafe text *(kept from v2)*
- student submission is validated and stored
- invalid submissions return 400 with the validation message
- submission endpoint rejects an unknown preference type and wrong method *(extends v2)*
- database faults return a generic 500 without internal details
- missing DATABASE_URL is reported as 503
- admin endpoint rejects a missing or wrong password before touching the database *(extends v2)*
- admin snapshot includes exact student and instructor CSV outputs *(kept from v2, now database-backed)*
- admin rejects unknown actions, including inherited object keys
- published results are served publicly with a short cache *(regression for a v2 header bug)*
- clear_demo_data removes submissions
- solver endpoint explains the manual fallback when no backend URL exists *(kept from v2)*
- solver endpoint sends the exports and publishes the returned slotbook
- solver failures are reported as 502 with the solver's message
- health reports configuration without connecting to the database
- TLS is required for remote databases and skipped for localhost

Retired: "Apps Script ContentService redirects are retrieved with a GET", since the Apps Script bridge no longer exists.

### `tests/gradecard.test.mjs` (9, new in v3)

Uses only synthetic cards from `tests/fixtures`.

- U, W and I do not count as completed; every other grade does
- course rows are read by column, with multi-cell titles joined
- headers, footers and totals are ignored
- a course row with a damaged column is reported, not guessed
- a retaken course counts once, from the latest passing attempt
- the roll number is read from the header
- text items on the same baseline become one row, left to right
- the synthetic PDF grade card parses end to end
- uploads that are not usable PDFs fail with a clear message

### `tests/prerequisites.test.mjs` (10, new in v3)

- no warnings when every prerequisite is completed
- one warning per missing prerequisite, in a stable order
- courses without rules never warn
- without any completion record a single summary warning is given
- snapshot warnings cover every student
- the grade card is the default completion source and unknown sources fail loudly
- end to end: professor declares, student uploads, warnings returned but submission saved
- end to end: an unreadable card and a roll mismatch are warnings, not errors
- end to end: resubmitting without a card keeps the earlier card
- end to end: admin snapshot exports prerequisites, completions and warnings for the Java CLI

### `tests/parity.test.mjs` (2, new in v3)

- portal warnings equal the shared golden file used by the Java tests
- U and W rows from a CSV import do not count as completed

## Portal integration (`npm run test:integration`, 4, new in v3)

Real Postgres, covering what pg-mem cannot (it does not implement `ROLLBACK`). **Drops and recreates the `public` schema**: point it only at a throwaway database.

- migrations run on real Postgres and are idempotent
- a failing write rolls back the whole student submission
- concurrent resubmissions leave exactly one complete preference list
- prerequisite replacement is atomic with the instructor submission

## Static checks (`npm run check`)

Required files exist, every JS file parses, JSON is valid, HTML asset links resolve, catalog IDs are unique, the two required Java CSV headers match the fixtures. New in v3:

- `.env.example` keeps `DATABASE_URL` as a placeholder and contains no Neon password;
- migrations are numbered without gaps; a `DROP TABLE` needs a `-- destructive: reviewed` note;
- every committed PDF fixture with text carries the `SYNTHETIC` marker. This was checked by temporarily adding a real grade card, which the check rejected.

## CLI demonstrations in CI

| Run | Checks |
|---|---|
| `data/feasible`, UniTime and CP-SAT | Exit 0 and exports written (v2) |
| `data/demo-showcase`, UniTime and CP-SAT | Showcase outputs uploaded as an artifact (v2) |
| `data/prerequisite-warnings` **(new in v3)** | Prints `Prerequisite warnings: 22`; exported CSV equals the golden file |

## Manual checks

| Check | How | Result for v3 |
|---|---|---|
| Student form with a grade card | `npm run dev`, upload `tests/fixtures/synthetic_grade_card.pdf`, rank EE5141 after a professor lists EE1101 and EE2001 | Saved, with one warning for EE2001 (W on the card) |
| Professor prerequisites | Choose EE5141, enter `EE1101, EE2001` | "2 prerequisite(s) recorded for EE5141" |
| Admin warnings panel | Load dashboard | 2 rules, 1 warning, row shown; three new CSV downloads |
| Real IITM grade card | Parse a real card locally, never committed | 43 of 43 course rows read, matching `pdftotext` |
| No JavaScript errors | Headless Chrome console during the run above | None |

v2's manual JavaFX checks still apply; see the [v2 plan](../docs_v2/TEST_PLAN.md).
