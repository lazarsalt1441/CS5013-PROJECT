# IITM Explainable Slotbook: Functionality and Progress

Status as of **2 November 2026** (`main` at `8f2c000`, v3).

This document describes what the tool does today and how the project got here. Detail lives in the [manual](MANUAL.md), the [`slotbook(1)` man page](man/slotbook.1.txt), the [v3 documentation](docs_v3/README.md) and the [prompt log](prompts/PROMPT_LOG.md).

## 1. What the tool is

The tool builds a weekly course slotbook for an IITM department. It assigns every course one lecture slot from the institute grid, never breaking a hard rule, and keeps instructor penalties, movement from historical slots and student clashes as low as possible. Every assignment comes with a deterministic explanation: why this slot, and why not each of the others.

| Component | Technology | Role |
|---|---|---|
| Engine | Java 17, UniTime cpsolver, OR-Tools CP-SAT | Validate input, solve, explain, export |
| CLI (`slotbook`) | Java, run through Maven | Headless runs, CI demonstrations |
| Desktop view | JavaFX | Pick a dataset and solver, inspect assignments and explanations |
| Preference portal | Vercel functions, Postgres on Neon | Collect student, professor and prerequisite input; export CSVs; publish results |

## 2. Functionality

Status: **Done** means covered by automated tests and a recorded run. **Partial** means it works but has a stated gap. **Planned** means designed for, but not built.

### Solving and explaining

| Feature | Status | Evidence |
|---|---|---|
| CSV input for courses, slots, conflicts, instructor and student preferences | Done | `DatasetLoaderTest`, `CsvSupportTest` |
| 18 pre-solve validation checks with specific issue codes | Done | `InputValidatorTest` |
| Hard rules: allowed or fixed slot, own lab, protected pairs, instructor, room | Done | Both solver test classes, `ConstraintChecker` oracle |
| Clashes from real meeting times (official IITM grid, to 5 minutes) | Done | `timeGridUsesRealMinutes` |
| Soft costs: instructor penalty, historical-slot move, student clashes | Done | `PenaltyCalculator` oracle equals both solvers' objective |
| Default solver: UniTime cpsolver (IFS construction, hill climbing, great deluge) | Done | `UniTimeTimetableSolverTest` |
| Exact reference solver: CP-SAT, proves optimality on small inputs | Done | `OrToolsTimetableSolverTest`, brute-force comparison on 10 seeds |
| Deterministic explanations with rejected alternatives, identical for both solvers | Done | `emitsOneCompleteExplanationPerCourse` (both solvers) |
| Diagnostics for infeasible input | Done | `rejectsKnownInfeasibleFixtureBeforeSolving` |
| Export: `generated_slotbook.csv`, `assignment_explanations.csv` | Done | `SlotbookExporterTest` |

### Prerequisites (v3)

| Feature | Status | Evidence |
|---|---|---|
| Professors declare prerequisites per course | Done | Store and end-to-end portal tests |
| Students upload a PDF grade card; U, W and I count as not completed; retakes handled | Done | `gradecard.test.mjs`; a real IITM card read 43 of 43 rows (not committed) |
| Advisory warnings at submission, on the admin dashboard and in the CLI; never blocking | Done | `prerequisites.test.mjs`, `PrerequisiteCheckerTest` |
| Portal and CLI give identical warnings | Done | Shared golden file checked by both suites and by CI |
| Warnings never change the solver input | Done | `prerequisiteInputsNeverChangeWhatTheSolverReceives` |
| Verified completions from the academic section (`admin` source) | Planned | Extension point in `api/_completions.js`; the schema already allows it |
| Warnings shown in the JavaFX view | Partial | The CSV is exported; there is no on-screen panel yet |

### Preference portal

| Feature | Status | Evidence |
|---|---|---|
| Student ranking form (1–10 courses) | Done | API and validation tests; browser run |
| Professor slot-penalty form with reasons | Done | API and validation tests; browser run |
| Admin dashboard: counts, previews, CSV exports, reset | Done | API tests; browser run |
| Exports matching the Java CSV schemas exactly, plus `prerequisites.csv` and `completed_courses.csv` | Done | Contract tests; `npm run check` header check |
| Publish results manually or through an optional solver API | Done | Solver and publication tests |
| Public results page, latest run, earlier runs kept | Done | `public results show only the latest published run` |
| Postgres storage with migrations, constraints and transactions | Done | 8 store tests; 4 integration tests on Postgres 16 |
| Student and professor authentication | Planned | Demonstration only; the UI says so |
| Live deployment of v3 | Pending | Needs the team's Neon database and Vercel settings (see `portal/README.md`) |

### User roles

| Role | Can do |
|---|---|
| Student | Rank courses, upload a grade card, see prerequisite warnings |
| Professor | Set slot penalties with reasons, declare prerequisites for a course |
| Administrator | Monitor submissions and warnings, export CSVs, run or upload the solver result, publish, reset |
| Anyone | View the published slotbook and explanations |
| Timetabler (CLI or desktop) | Solve any dataset with either solver, compare them, read explanations and warnings |

## 3. Progress

### Milestones

| Date | Version | Prompts | Delivered | Commits |
|---|---|---|---|---|
| 10 Oct 2026 | v1 | P001–P005 | Architecture and prompt workflow; mock slotbook data; CP-SAT model with explanations; JavaFX view; CLI; CI | `e6cf06a`..`ef61efe` |
| 10 Oct 2026 | v1 testing | P008–P010 | File-structure docs; independent penalty, constraint and brute-force oracles; generated scenarios | `7abd006`..`e4e7ca9` |
| 10 Oct 2026 | v2 | P006, P011 | UniTime cpsolver wrapper as default solver; real IITM times, rooms and labs; solver comparison on every scenario; docs_v2 | `cfbe4f5`..`f1c4b47` |
| 11 Oct 2026 | v2 portal | P012 | Vercel preference portal on Google Sheets; Apps Script bridge; CSV exports; solver hook | `dcca87b`..`a15b891` |
| 11 Oct 2026 | Mid-evaluation | P013–P014 | Explainable showcase dataset; three-page practices PDF; viva pack | `74c4f64`..`d4c2f39` |
| 2 Nov 2026 | v3 | P015 | Postgres on Neon; grade-card prerequisite warnings in portal and CLI; man page; docs_v3 | `ebb9c28`..`66ff082`, merged in `8f2c000` (PR #1) |
| 2 Nov 2026 | v3 docs | P016 | This document | this commit |

### Growth in testing

| Point | Java tests | Portal tests | Notes |
|---|---|---|---|
| First CP-SAT build (v1) | 11 | none | `docs_v1/VERIFICATION.md` |
| v1 with testing framework | 41 | none | Oracles, generated scenarios, brute force |
| v2 | 52 | 7 | Every scenario solved by both solvers; portal contract tests with a mocked Apps Script |
| v3 | 71 | 56 + 4 integration | Database-backed contract tests, grade-card parser, prerequisite parity, real-Postgres integration |

CI on `main` runs:
- both test suites, with a Postgres 16 service for the integration tests;
- CLI demonstrations with both solvers;
- the prerequisite demo against its golden file;
- a man-page lint.

### Results so far

- On `data/feasible`, UniTime reaches objective 20, the optimum CP-SAT proves.
- Across the generated scenarios, UniTime matched CP-SAT's proven optimum in 10 of 13 and was 1, 8 and 4 points higher in the others. On the 300-student scenario it beat time-limited CP-SAT, 259 to 261. See the [comparison](docs_v2/TEST_PLAN.md#solver-comparison).
- The mid-evaluation showcase (11 courses, 36 students) reaches objective 9 with UniTime, equal to the CP-SAT optimum.
- On `data/prerequisite-warnings`, the CLI reports 22 warnings and produces the same slotbook as `data/feasible`.

### Engineering practice

| Practice | How it shows in the repository |
|---|---|
| Prompt logging | 16 entries in [`PROMPT_LOG.md`](prompts/PROMPT_LOG.md), each with the original prompt, a CoSTAR restatement, negative constraints, review and acceptance; logged before the code they produced |
| Version control | 49 commits on `main`, one coherent step each; v3 delivered through a reviewed pull request with CI |
| Testing | Independent oracles, generated scenarios, cross-solver and cross-language checks, regression tests for each fault found |
| Debugging | Faults recorded with reproduce, observe, hypothesize, isolate, correct, test: [v2](docs_v2/DEBUGGING.md), [v3](docs_v3/DEBUGGING.md) (8 faults) |
| Documentation | Per-version docs, manual, man page, test plans listing every test, verification records |

## 4. Known limitations

- UniTime is a heuristic; use `--solver cpsat` when proof of optimality matters.
- Rooms are fixed per course and treated as large enough; room capacity and choice are not modelled.
- Student and instructor data in the repository are synthetic.
- The portal has no login; grade cards are self-reported and unverified.
- The JavaFX view does not display prerequisite warnings yet.
- The Java solver runs outside Vercel; the portal reaches it only through the optional `SOLVER_API_URL` hook or the manual CSV workflow.

## 5. Next steps

1. Deploy v3: Neon database, migrations, Vercel environment and Node 22 ([portal README](portal/README.md)).
2. Add IITM login before any real student data is collected.
3. Build the `admin` completion source, so the academic section can supply verified records.
4. Show prerequisite warnings in the JavaFX view.
5. Deploy a small HTTP wrapper for the Java solver, so "Generate slotbook" works from the admin page.
