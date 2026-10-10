# Test Plan (v2)

Run everything with `mvn clean test`. It runs 52 tests in 9 classes.

## Automated tests

| Test class | Behaviour verified |
|---|---|
| `CsvSupportTest` | Quoted commas and escaped quotes parse correctly |
| `DatasetLoaderTest` | All five fixture types load with expected counts (17 slots); student selections produce positive co-demand weights |
| `InputValidatorTest` | Feasible fixture is clean; contradictory fixed courses give `FIXED_CONFLICT` |
| `InputValidatorTest` | Lab slot as lecture, unknown historical slot, unknown lab, fixed slot overlapping own lab, duplicate preference and negative penalty each give their own issue code |
| `OrToolsTimetableSolverTest` | CP-SAT: every course assigned, inside its allowed slots, fixed slots respected, hard conflicts separated, one complete explanation per course, invalid fixture rejected |
| `UniTimeTimetableSolverTest` | UniTime: `FEASIBLE` with no hard-rule violations; EE1100 and EE1101 no longer share room CRC in slot E |
| `UniTimeTimetableSolverTest` | Reported objective equals `PenaltyCalculator`, and equals CP-SAT's proven optimum (20) on `data/feasible` |
| `UniTimeTimetableSolverTest` | One complete explanation per course; same iteration cap gives the same timetable; invalid fixture rejected |
| `SlotbookExporterTest` | Both CSV exports exist and contain assignment and explanation evidence |
| `TestingFrameworkTest` | Penalty oracle, constraint checker and brute force agree with hand-computed cases; generated scenarios keep their planted timetable feasible; CSV writer round-trips |
| `TestingFrameworkTest` | `TimeGrid` uses real minutes (a 14:30 lecture overlaps a 14:00–16:45 lab; back-to-back meetings don't); own-lab and room clashes are flagged; lab overlaps count toward student penalties |
| `GeneratedScenarioTest` | Each of 14 generated scenarios is solved by both solvers (see below) |
| `GeneratedScenarioTest` | Re-solving gives the same objective (CP-SAT) and the same timetable (UniTime with an iteration cap) |
| `BruteForceOptimalityTest` | On 10 random 6-course sub-problems CP-SAT returns `OPTIMAL` with the exhaustive minimum, and UniTime finds a feasible answer; its gap is printed |

## Solver comparison

For every scenario, both solvers must:

- find a timetable (the planted timetable proves one exists);
- break no hard rule, according to `ConstraintChecker`;
- report an objective equal to `PenaltyCalculator`'s recomputation;
- do no worse than the planted timetable.

In addition, UniTime must never beat a CP-SAT result proven `OPTIMAL`. UniTime's gap to CP-SAT is reported, not asserted, because UniTime is a heuristic.

Results recorded on 10 October 2026, with UniTime capped at 50,000 iterations:

| Scenario | UniTime | CP-SAT | Gap | Planted |
|---|---|---|---|---|
| baseline | 20 | 20 OPTIMAL | 0 | 20 |
| no-history | 10 | 10 OPTIMAL | 0 | 12 |
| no-students | 14 | 14 OPTIMAL | 0 | 17 |
| students-300 | **259** | 261 FEASIBLE (10 s limit) | −2 | 560 |
| random-preferences | 30 | 30 OPTIMAL | 0 | 32 |
| restricted-slots | 20 | 20 OPTIMAL | 0 | 20 |
| extra-conflicts | 20 | 20 OPTIMAL | 0 | 20 |
| shared-instructors | 30 | 26 OPTIMAL | +4 | 30 |
| random-1 | 288 | 280 OPTIMAL | +8 | 642 |
| random-2 | 117 | 116 OPTIMAL | +1 | 338 |
| random-3 | 332 | 332 OPTIMAL | 0 | 573 |
| random-4 | 190 | 190 OPTIMAL | 0 | 387 |
| random-5 | 327 | 327 OPTIMAL | 0 | 554 |
| random-6 | 181 | 181 OPTIMAL | 0 | 317 |

- UniTime matched the proven optimum in 10 of the 13 scenarios CP-SAT solved to optimality. The other three were above it: random-2 by 1 (1%), random-1 by 8 (3%) and shared-instructors by 4 (15%). shared-instructors packs 18 courses onto 7 instructors, and improving it needs several courses moved at once.
- On the 300-student scenario it beat CP-SAT's time-limited answer.
- It hit the exhaustive optimum on all 10 brute-force sub-problems.
- Each UniTime test run took about 0.5 s (50,000 iterations).
- The table repeats exactly across runs, except CP-SAT's time-limited `students-300` result, which can vary slightly.

### Choosing UniTime's search algorithm

UniTime ships two course timetabling algorithms:

- **Default:** iterative forward search that keeps repairing a complete timetable.
- **Deluge:** construction, then hill climbing, then great deluge.

Both were run on every generated dataset with a 2-second limit:

| | Deluge better | Same | Default better |
|---|---|---|---|
| Scenarios | 6 (random-1, 2, 4, 5, 6, random-preferences) | 6 | 2 (shared-instructors 26 vs 30, students-300 258 vs 259) |

The wrapper uses Deluge, with UniTime's stock moves: time and room change, swaps, and suggestions. Without those moves the search stalled right after construction.

## Generated-scenario framework

The helpers in `src/test/java/in/ac/iitm/slotbook/testing/` derive seeded dummy datasets from `data/feasible`:

| Class | Role |
|---|---|
| `ScenarioGenerator` | Builds the scenarios: baseline, no history, no students, 300 synthetic students, random instructor preferences, restricted slots, extra conflicts, shared instructors, and `random-N` combinations. It draws only from lecture slots |
| `PenaltyCalculator` | Recomputes instructor, historical and student penalties without solver code; shares only `TimeGrid` |
| `ConstraintChecker` | Lists every violation: allowed or fixed slot, own lab, instructor, room, hard conflict |
| `BruteForceSolver` | Exhaustive optimum for problems of at most 7 courses |
| `ScenarioCsvWriter` | Writes a scenario as a five-CSV dataset, including slot lengths |
| `PenaltyReport` | Prints the UniTime-vs-CP-SAT table and writes it as CSV |

Every scenario is built around a *planted* timetable: the CP-SAT baseline answer. Generated constraints never contradict it, and conflicts and shared instructors are only added between courses whose planted times don't overlap. So each scenario is guaranteed feasible, and the planted penalty is an upper bound on the optimum.

After `mvn test`:

- `target/penalty-report/penalty_report.csv` holds the comparison table, which is also printed in the test output.
- `target/generated-scenarios/<name>/` holds each dataset. Replay one with `mvn exec:java -Dexec.args="--input target/generated-scenarios/random-3 --solver unitime"` (or `cpsat`).

Change `SEED`, `RANDOM_SCENARIOS` or `UNITIME_ITERATIONS` in `GeneratedScenarioTest` to explore other cases.

## Manual smoke tests

1. Run `mvn javafx:run`.
2. Load `data/feasible`, leave the solver as **UniTime (cpsolver)** and click **Generate slotbook**.
3. Confirm 18 rows appear and the status line reads `UniTime (cpsolver) | status: FEASIBLE | objective: 20`.
4. Select `EE2002` and confirm its explanation identifies fixed slot B.
5. Select `EE3001` and confirm it mentions its lab in slot P.
6. Select a non-fixed course and confirm all eleven alternative lecture slots are classified.
7. Switch the solver to **OR-Tools CP-SAT (v1)**, generate again, and confirm the objective is still 20 with status `OPTIMAL`.
8. Export to a new directory and open both CSV files.
9. Load `data/infeasible-fixed-conflict`, confirm `FIXED_CONFLICT` appears and no timetable is displayed.

## Regression policy

- Run the full suite after every behavioural change.
- Do not assert one exact timetable unless uniqueness is intentionally guaranteed.
- Never assert that UniTime is optimal; assert feasibility, correctness of its reported penalty, and bounds.
- Add a negative test for every validation or parsing bug.
- Keep generated outputs out of Git; retain only deterministic input fixtures and test expectations.
