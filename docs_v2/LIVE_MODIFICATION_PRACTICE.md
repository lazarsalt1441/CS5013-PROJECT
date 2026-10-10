# Live-Modification Practice (v2)

Use these as short individual exercises before the evaluation. After each change, run `mvn test` and explain which layer owns the behaviour.

## 1. Change an objective weight

Change `Objective.STUDENT_CONFLICT_MULTIPLIER` from 3 to 4. Run both solvers on `data/feasible` and explain why the timetable can change even though every hard constraint still holds. Then explain why `UniTimeTimetableSolver` needs no edit: it reads the multiplier from `Objective`.

What this tests: hard versus soft constraints, and a single source of truth for weights.

## 2. Add a validation rule

Reject a course whose `class_size` is not positive. Add the rule in `InputValidator`, give it an issue code, build a minimal in-memory problem in `InputValidatorTest`, and assert the code rather than the message.

What this tests: domain invariants and negative-path testing.

## 3. Add a UniTime distribution preference

Add a soft "same days" rule for two courses. In `UniTimeModelBuilder`, create a `GroupConstraint` with `ConstraintType.SAME_DAYS` and preference `Constants.sPreferenceDiscouraged`, and give `DistributionPreferences` a non-zero weight. Explain why CP-SAT and `PenaltyCalculator` must change too before the equivalence tests pass again.

What this tests: understanding the mapping, and the cost of keeping two solvers in step.

## 4. Use the M.Tech slot grid

Copy `data/feasible` and edit `slots.csv` so the fifth morning period is 12:00 instead of 13:00. Explain why no Java changes: times are data, and `TimeGrid` derives overlaps from them.

What this tests: separation of data and logic.

## 5. Add an export field

Add `solver` to `generated_slotbook.csv`. Pass the solver name through `SlotbookRun` and update `SlotbookExporterTest` to check the header without assuming one exact timetable.

What this tests: public output contracts and resilient assertions.

## 6. Shorten UniTime runs

Add a `--max-iterations N` CLI option and pass it to `UniTimeTimetableSolver`'s iteration cap. Validate that it is positive and document it in `MANUAL.md`.

What this tests: argument parsing and dependency flow from CLI to service to solver.

## Safe workflow during the evaluation

1. Restate the requested behaviour and identify its owning module.
2. Find the nearest test and run it before editing.
3. Make the smallest coherent change.
4. Add or update one focused test.
5. Run the focused test, then the complete suite.
6. Explain the data flow and any trade-off introduced.
