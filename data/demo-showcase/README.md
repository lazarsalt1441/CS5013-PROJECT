# Mid-Evaluation Showcase Dataset

This synthetic scenario is small enough to explain in two minutes but rich enough to demonstrate every important scheduling trade-off.

## Story

- Two large core courses are administratively fixed to slots B and C.
- One core course has a fixed Friday laboratory and must avoid lecture choices that overlap it.
- Eight popular electives compete for seven standard lecture slots, so the model must decide which low-weight student conflict is least harmful.
- Thirty-six anonymous students form four recognisable demand clusters plus six pair-coverage profiles. Together they create co-demand between every pair of the eight electives.
- Faculty preferences deliberately disagree with some historical slots and student-demand patterns.
- Protected curriculum pairs, shared rooms, fixed courses, and the laboratory remain hard constraints.

## Expected demonstration

1. Run the validator to show that all five input files are consistent.
2. Generate the timetable with UniTime.
3. Point out the fixed B and C assignments and the Friday laboratory.
4. Open one elective explanation to show the instructor penalty, historical stability cost, student co-demand contribution, and rejected alternatives.
5. Compare with `--solver cpsat` to show that the wrapper and reference solver share the same objective.

## Commands

```bash
mvn exec:java -Dexec.args="--input data/demo-showcase --output output/demo-showcase --time-limit 10"
mvn exec:java -Dexec.args="--input data/demo-showcase --output output/demo-showcase-cpsat --solver cpsat --time-limit 10"
```

The scenario contains only synthetic student identifiers and preferences.

## Verified result

GitHub Actions generated both result sets from commit `49f663a`:

| Solver | Status | Objective | Runtime |
|---|---|---:|---:|
| UniTime cpsolver | Feasible | 9 | 10.108 s |
| OR-Tools CP-SAT | Optimal | 9 | 0.199 s |

UniTime therefore matched the CP-SAT proven optimum on this case. The solvers may choose different timetables with the same total cost.

The key visible trade-off is that EE3110 and EE5175 share slot B. Because all 28 elective pairs have positive co-demand but only seven lecture slots are available, at least one elective pair must overlap. The solver leaves the lowest-weight pair together, producing a student-conflict contribution of 3 for each course explanation while preserving every hard constraint.

Generated files are retained under `generated/unitime` and `generated/cpsat` so the result can be demonstrated without rerunning Maven.
