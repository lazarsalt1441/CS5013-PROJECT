# Verification Record (v2)

Date: 11 October 2026

## Environment

- Java: Oracle JDK 26.0.2 (the build targets release 17; CI uses Temurin 17)
- Build: Apache Maven 3.9.9
- Solvers: UniTime cpsolver 1.4.94; Google OR-Tools 9.11.4210
- Tests: JUnit Jupiter 5.11.3

## Automated result

```text
mvn test
Tests run: 52, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS   (about 31 s)
```

Two full runs in separate JVMs produced identical solver-comparison reports, apart from CP-SAT's time-limited `students-300` result. The comparison table is in [TEST_PLAN.md](TEST_PLAN.md#solver-comparison).

## Feasible CLI result, UniTime (default)

```text
Solver: UniTime (cpsolver)
Status: FEASIBLE
Objective: 20
Runtime: 10.099 s
Iterations: 1031078
Best solution found at iteration 23 after 0.044 s
Assignments produced: 18 / 18
Exports produced: generated_slotbook.csv, assignment_explanations.csv
```

## Feasible CLI result, CP-SAT

```text
Solver: OR-Tools CP-SAT (v1)
Status: OPTIMAL
Objective: 20
Runtime: 0.181 s
```

Both solvers reach the proven optimum of 20. The v1 result was 17, but it placed EE1100 and EE1101 in room CRC at the same time, slot E. v2 forbids that clash, and the separation costs 3. `UniTimeTimetableSolverTest.separatesTheRoomClashFromVersionOne` checks it.

The test suite separately verifies, for both solvers, that every assignment:

- is in its course's allowed set;
- respects fixed slots;
- doesn't overlap its own lab;
- separates every hard-conflict pair;
- double-books no instructor or room.

## Negative fixture result (both solvers)

```text
Status: INVALID_INPUT
Diagnostic: FIXED_CONFLICT: EE9001 and EE9002 are both fixed to A
but cannot clash: Both are compulsory for the same cohort
```

## Not verified in this record

- **JavaFX app:** the manual smoke test in [TEST_PLAN.md](TEST_PLAN.md#manual-smoke-tests) needs a graphical desktop and wasn't run. All JavaFX sources compile.

## Mid-evaluation readiness

- The portal contract suite passes all 7 Node tests.
- The portal validator passes with 18 catalog courses, 17 slots, and 21 required assets.
- The showcase validator passes with 11 courses, 36 synthetic students, 144 preference rows, and 6 protected pairs.
- CI generated the showcase with both engines: UniTime objective 9 and CP-SAT proven optimum 9.
- The UniTime result is published at <https://cs5013-project-portal.vercel.app/results>, with 11 inspectable course explanations.
- `mid_evaluation/principles_and_practices.pdf` contains exactly three Letter pages; all pages were rendered and visually inspected, and the editable DOCX has no high, medium, or low accessibility findings.
