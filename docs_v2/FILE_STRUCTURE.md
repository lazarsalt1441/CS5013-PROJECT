# File Structure (v2)

Tracked files only. `target/`, `.idea/` and generated `output/*` are git-ignored.

```text
CS5013-PROJECT/
├── .github/workflows/ci.yml        GitHub Actions: mvn test + CLI demo on every push / PR
├── .gitattributes
├── .gitignore
├── pom.xml                         Maven build: Java 17, UniTime cpsolver, OR-Tools, JavaFX, JUnit
├── README.md                       Overview and quick start (v2), links to both doc sets
├── MANUAL.md                       CLI synopsis, options, inputs, outputs, limitations (v2)
│
├── data/                           Input datasets (five CSVs each)
│   ├── feasible/                   Demo dataset that solves
│   │   ├── courses.csv             Course metadata, allowed / fixed / historical slots, lab, room
│   │   ├── slots.csv               Official IITM grid: days, start times, lengths, LECTURE/LAB
│   │   ├── hard_conflicts.csv      Course pairs that must never overlap
│   │   ├── instructor_preferences.csv  Per-instructor, per-slot penalties
│   │   └── student_preferences.csv Ranked course choices per student
│   └── infeasible-fixed-conflict/  Same five files; two clashing courses are fixed to one slot
│
├── src/main/java/in/ac/iitm/slotbook/
│   ├── cli/SlotbookCli.java        Terminal entry point (--input, --output, --solver, --time-limit)
│   ├── ui/SlotbookApplication.java JavaFX entry point (load, choose solver, solve, inspect, export)
│   ├── service/
│   │   ├── SlotbookService.java    Facade: load -> solve; shared by CLI and UI
│   │   └── SlotbookRun.java        Record bundling problem + solution
│   ├── io/
│   │   ├── CsvSupport.java         Low-level CSV reading / required-column checks
│   │   ├── DatasetLoader.java      Five CSVs -> TimetableProblem
│   │   └── SlotbookExporter.java   Solution -> generated_slotbook.csv, assignment_explanations.csv
│   ├── model/                      Immutable records / enums
│   │   ├── Course, Slot (meetings, 5-minute cells), HardConflict, InstructorPreference,
│   │   │   StudentSelection, CoursePair
│   │   ├── TimeGrid.java           The one definition of overlap: lectures, labs, rooms
│   │   ├── TimetableProblem.java   All inputs + derived student pair weights
│   │   ├── TimetableSolution.java  Status, assignments, explanations, diagnostics, objective
│   │   └── AssignmentExplanation.java, ValidationIssue.java, SolveStatus.java
│   ├── validation/InputValidator.java  18 pre-solver checks
│   ├── solver/
│   │   ├── TimetableSolver.java    Interface implemented by both solvers
│   │   ├── Objective.java          Shared penalty weights and per-slot course penalty
│   │   ├── SolverChoice.java       unitime | cpsat, used by CLI and UI
│   │   ├── OrToolsTimetableSolver.java  v1 CP-SAT model (exact reference)
│   │   └── unitime/
│   │       ├── UniTimeModelBuilder.java     Slotbook problem -> UniTime TimetableModel
│   │       └── UniTimeTimetableSolver.java  Runs UniTime's search, maps the result back (default)
│   └── explain/ExplanationBuilder.java  Deterministic reasons + rejected alternatives
│
├── src/test/java/in/ac/iitm/slotbook/
│   ├── io/                         CSV parsing, loading, export tests
│   ├── validation/InputValidatorTest.java
│   ├── solver/
│   │   ├── OrToolsTimetableSolverTest.java
│   │   ├── unitime/UniTimeTimetableSolverTest.java  Feasibility, room fix, objective equivalence
│   │   ├── GeneratedScenarioTest.java     Both solvers on 14 scenarios; prints comparison
│   │   └── BruteForceOptimalityTest.java  CP-SAT vs exhaustive optimum; UniTime gap
│   └── testing/                    Test framework (see TEST_PLAN.md)
│       ├── ScenarioGenerator, Scenario     Seeded dummy datasets derived from data/feasible
│       ├── PenaltyCalculator, ConstraintChecker, BruteForceSolver   Independent oracles
│       ├── ScenarioCsvWriter, PenaltyReport                         Dataset + report output
│       └── TestingFrameworkTest            Self-tests for the oracles and TimeGrid
│
├── docs_v1/                        v1 (CP-SAT) documentation, README and MANUAL as they were
├── docs_v2/                        This documentation set (UniTime wrapper)
├── prompts/                        AI prompt log and review checklist
├── scripts/                        verify.sh, build_practices_pdf.py (v1 PDF)
└── output/.gitkeep                 Placeholder; real results are git-ignored
```

## Execution flow

```text
SlotbookCli / SlotbookApplication  (SolverChoice: unitime | cpsat)
        │
        ▼
SlotbookService.run(datasetDir)
        │  1. DatasetLoader.load()                5 CSVs -> TimetableProblem
        │  2. solver.solve(problem)
        │       a. InputValidator                  bad input -> INVALID_INPUT, stop
        │       b. unitime: UniTimeModelBuilder -> cpsolver search (10 s, seed 5013)
        │          cpsat:   CP-SAT model -> solve (10 s, 1 worker, seed 5013)
        │       c. map result to slot letters, ExplanationBuilder.buildAll()
        ▼
SlotbookRun(problem, solution)
        ▼
SlotbookExporter.export()  ->  generated_slotbook.csv + assignment_explanations.csv
```
