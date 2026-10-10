# Live-Modification Practice

Use these as short individual exercises before the evaluation. After each change, run `mvn test` and explain which layer owns the behaviour.

## 1. Add a CLI option

Add `--time-limit <seconds>` in `SlotbookCli`, pass it through `SlotbookService`, and use it in `OrToolsTimetableSolver`. Validate that it is positive and document it in `MANUAL.md`.

What this tests: argument parsing, dependency flow, validation, and avoiding a UI-to-solver shortcut.

## 2. Add one validation rule

Reject a course whose `allowed_slots` column is empty. Add the rule in `InputValidator`, create a minimal test fixture, and assert the issue code rather than the full prose message.

What this tests: domain invariants and negative-path testing.

## 3. Change an objective weight

Move the student-clash multiplier from a literal in `OrToolsTimetableSolver` to a named constant. Change it from 3 to 4, solve again, and explain why the timetable can change even though all hard constraints remain satisfied.

What this tests: hard versus soft constraints and objective interpretation.

## 4. Add an export field

Add `objective_score` to `slotbook.csv`. Update `SlotbookExporterTest` to check the header and a data row without assuming one exact timetable.

What this tests: public output contracts and resilient assertions.

## 5. Add an explanation reason

When the assigned slot equals the course's historical slot, add a reason stating that the stability penalty was avoided. Keep this deterministic and test for the semantic fact, not sentence punctuation.

What this tests: evidence-backed explanations and separation from optimization.

## Safe workflow during the evaluation

1. Restate the requested behaviour and identify its owning module.
2. Find the nearest test and run it before editing.
3. Make the smallest coherent change.
4. Add or update one focused test.
5. Run the focused test, then the complete suite.
6. Explain the data flow and any trade-off introduced.
