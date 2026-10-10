# File Structure

Tracked files only (`target/`, `.idea/` and generated `output/*` are git-ignored).

```text
CS5013-PROJECT/
├── .github/workflows/ci.yml        GitHub Actions: runs `mvn test` on every push / PR
├── .gitattributes
├── .gitignore
├── pom.xml                         Maven build: Java 17, OR-Tools, JavaFX, JUnit
├── README.md                       Overview and quick start
├── MANUAL.md                       CLI synopsis, options, inputs, outputs, limitations
│
├── data/                           Input datasets (five CSVs each)
│   ├── feasible/                   Demo dataset that solves
│   │   ├── courses.csv             Course metadata, allowed / fixed / historical slots
│   │   ├── slots.csv               Slot id, days, start times, category
│   │   ├── hard_conflicts.csv      Course pairs that must never share a slot
│   │   ├── instructor_preferences.csv  Per-instructor, per-slot penalties
│   │   └── student_preferences.csv Ranked course choices per student
│   └── infeasible-fixed-conflict/  Same five files; two clashing courses are fixed to one slot
│
├── src/main/java/in/ac/iitm/slotbook/
│   ├── cli/SlotbookCli.java        Terminal entry point (--input, --output)
│   ├── ui/SlotbookApplication.java JavaFX entry point (load, solve, inspect, export)
│   ├── service/
│   │   ├── SlotbookService.java    Facade: load -> solve; shared by CLI and UI
│   │   └── SlotbookRun.java        Record bundling problem + solution
│   ├── io/
│   │   ├── CsvSupport.java         Low-level CSV reading / required-column checks
│   │   ├── DatasetLoader.java      Five CSVs -> TimetableProblem
│   │   └── SlotbookExporter.java   Solution -> generated_slotbook.csv, assignment_explanations.csv
│   ├── model/                      Immutable records / enums
│   │   ├── Course, Slot, HardConflict, InstructorPreference, StudentSelection, CoursePair
│   │   ├── TimetableProblem.java   All inputs + derived student pair weights
│   │   ├── TimetableSolution.java  Status, assignments, explanations, diagnostics, objective
│   │   ├── AssignmentExplanation.java, ValidationIssue.java, SolveStatus.java
│   ├── validation/InputValidator.java  Pre-solver sanity checks
│   ├── solver/
│   │   ├── TimetableSolver.java    Interface
│   │   └── OrToolsTimetableSolver.java  CP-SAT model and objective
│   └── explain/ExplanationBuilder.java  Deterministic reasons + rejected alternatives
│
├── src/test/java/in/ac/iitm/slotbook/   JUnit tests mirroring io/, solver/, validation/
│   ├── solver/GeneratedScenarioTest.java     Solves generated scenarios, prints penalty report
│   ├── solver/BruteForceOptimalityTest.java  CP-SAT vs exhaustive optimum on small problems
│   └── testing/                    Test framework (see docs/TEST_PLAN.md)
│       ├── ScenarioGenerator, Scenario     Seeded dummy datasets derived from data/feasible
│       ├── PenaltyCalculator, ConstraintChecker, BruteForceSolver   Independent oracles
│       ├── ScenarioCsvWriter, PenaltyReport                         Dataset + report output
│       └── TestingFrameworkTest            Self-tests for the oracles
│
├── docs/ (now docs_v1/)             Architecture, walkthrough, test plan, debugging,
│                                   verification, evaluation script, diagrams, PDF
├── prompts/                        AI prompt log and review checklist
├── scripts/                        verify.sh, build_practices_pdf.py
└── output/.gitkeep                 Placeholder; real results are git-ignored
```

## Execution flow

```text
SlotbookCli / SlotbookApplication
        │
        ▼
SlotbookService.run(datasetDir)
        │  1. DatasetLoader.load()          5 CSVs -> TimetableProblem
        │  2. OrToolsTimetableSolver.solve()
        │       a. InputValidator            bad input -> INVALID_INPUT, stop
        │       b. build CP-SAT model        (see below)
        │       c. solve (10 s, 1 worker, seed 5013 => reproducible)
        │       d. read assignments, ExplanationBuilder.buildAll()
        ▼
SlotbookRun(problem, solution)
        ▼
SlotbookExporter.export()  ->  generated_slotbook.csv + assignment_explanations.csv
```

## CP-SAT model

- **Variables:** one boolean `assign[course][slot]`, forced to 0 outside the course's allowed slots.
- **Hard constraints:** exactly one slot per course; fixed-slot courses forced to that slot; hard-conflict
  pairs never share a slot; courses with the same instructor never share a slot.
- **Objective (minimised):**
  - instructor penalty for the chosen slot (default 1 if no preference row);
  - plus 2 if the slot differs from the course's historical slot;
  - plus, for each pair of courses a student wants together, `weight x 3` when both land in the same
    slot. Pair weight is `max(1, 4 - worst rank)`, summed over students.
- **Outcome:** OPTIMAL / FEASIBLE give assignments and explanations; INFEASIBLE gives diagnostics only.
