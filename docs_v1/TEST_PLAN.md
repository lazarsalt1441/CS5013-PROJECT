# Test Plan

## Automated tests

| Test class | Behaviour verified |
|---|---|
| `CsvSupportTest` | Quoted commas and escaped quotes parse correctly |
| `DatasetLoaderTest` | All five fixture types load with expected counts |
| `DatasetLoaderTest` | Student selections produce positive co-demand weights |
| `InputValidatorTest` | Feasible fixture contains no validation errors |
| `InputValidatorTest` | Contradictory fixed courses produce `FIXED_CONFLICT` |
| `OrToolsTimetableSolverTest` | Every course receives an assignment |
| `OrToolsTimetableSolverTest` | Every assignment lies in the allowed domain and respects fixed slots |
| `OrToolsTimetableSolverTest` | Every hard-conflict pair receives different slots |
| `OrToolsTimetableSolverTest` | Every course receives a complete explanation |
| `OrToolsTimetableSolverTest` | Known invalid fixture is rejected before solving |
| `SlotbookExporterTest` | Both CSV exports exist and contain assignment/explanation evidence |
| `TestingFrameworkTest` | Penalty oracle, constraint checker and brute force agree with hand-computed cases |
| `TestingFrameworkTest` | Generated scenarios keep their planted assignment feasible; CSV writer round-trips |
| `GeneratedScenarioTest` | Each of 14 generated scenarios solves, violates no hard constraint, matches the recomputed penalty and does no worse than the planted assignment |
| `GeneratedScenarioTest` | Re-solving a scenario gives the same objective |
| `BruteForceOptimalityTest` | On 10 random 6-course sub-problems CP-SAT returns `OPTIMAL` with the exhaustive minimum |

## Generated-scenario framework

The helpers in `src/test/java/in/ac/iitm/slotbook/testing/` derive seeded dummy datasets from
`data/feasible`:

| Class | Role |
|---|---|
| `ScenarioGenerator` | Builds scenarios: baseline, no history, no students, 300 synthetic students, random instructor preferences, restricted slots, extra conflicts, shared instructors, and `random-N` combinations of all of them |
| `PenaltyCalculator` | Recomputes instructor, historical and student penalties without solver code |
| `ConstraintChecker` | Lists hard-constraint violations in any assignment |
| `BruteForceSolver` | Exhaustive optimum for problems of at most 7 courses |
| `ScenarioCsvWriter` | Writes a scenario as a five-CSV dataset |
| `PenaltyReport` | Prints the per-scenario penalty table and writes it as CSV |

Every scenario is built around a *planted* assignment (the baseline solution). Generated
constraints never contradict it, so each scenario is guaranteed feasible and the planted
penalty is an upper bound on the optimum. Scenarios whose only change adds constraints the
baseline already satisfies (`restricted-slots`, `extra-conflicts`) therefore keep the
baseline optimum; this is expected.

After `mvn test`:

- `target/penalty-report/penalty_report.csv` holds the per-scenario penalty breakdown,
  which is also printed in the test output.
- `target/generated-scenarios/<name>/` holds each dataset, which can be replayed with
  `mvn exec:java -Dexec.args="--input target/generated-scenarios/random-3"`.

Change `SEED` or `RANDOM_SCENARIOS` in `GeneratedScenarioTest` to explore other cases.
Scenarios with several hundred students may stop at the 10-second limit with status
`FEASIBLE` rather than `OPTIMAL`; the tests accept either.

## Manual smoke tests

1. Run `mvn javafx:run`.
2. Load `data/feasible` and click **Generate slotbook**.
3. Confirm 18 rows appear and status is `OPTIMAL` or `FEASIBLE`.
4. Select `EE2002`; confirm its explanation identifies fixed slot B.
5. Select a non-fixed course; confirm all six alternative slots are classified.
6. Export to a new directory and open both CSV files.
7. Load `data/infeasible-fixed-conflict`; confirm `FIXED_CONFLICT` appears and no timetable is displayed.

## Regression policy

- Run the full suite after every behavioural change.
- Do not assert one exact timetable unless uniqueness is intentionally guaranteed.
- Add a negative test for every validation or parsing bug.
- Keep generated outputs out of Git; retain only deterministic input fixtures and test expectations.
