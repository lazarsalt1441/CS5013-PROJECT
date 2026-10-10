# IITM Explainable Slotbook Manual (v2)

Version 1 manual: [docs_v1/MANUAL.md](../docs_v1/MANUAL.md).

## NAME

`slotbook` - generate and explain an IITM-style course slotbook from CSV constraints.

## SYNOPSIS

```bash
mvn clean test
mvn exec:java -Dexec.args="--input DIR [--output DIR] [--solver unitime|cpsat] [--time-limit SECONDS]"
mvn javafx:run
```

## DESCRIPTION

The tool reads five CSV files and validates their references and contradictions. It then builds a timetabling model and assigns one lecture slot to every course while minimising three transparent costs:

1. Instructor preference penalties (default 1 for a slot with no preference row).
2. A penalty of 2 for moving away from the historical slot.
3. Student clashes: for each pair of courses chosen by the same students, pair weight × overlapping meetings × 3.

Hard constraints always take precedence:

- exactly one lecture slot per course, inside its allowed set, and the fixed slot if one is given;
- a lecture may not overlap the course's own lab;
- protected course pairs may not overlap, labs included;
- an instructor may not teach two overlapping meetings, labs included;
- two courses with the same room may not have overlapping lectures.

Overlap is computed from real weekly times, not slot letters, so a lecture slot that runs into a lab block is detected.

By default the model is solved by UniTime's course timetabling engine (cpsolver), which builds a timetable and then keeps improving it until the time limit. `--solver cpsat` uses the v1 OR-Tools CP-SAT model instead, which can prove that its answer is optimal on small inputs.

The generated explanation is deterministic and identical for both solvers. It reports the constraints satisfied by the selected slot and that slot's penalties. For every other lecture slot it says why it was rejected or gives its estimated local cost. No LLM is involved.

## OPTIONS

### `--input DIR`

Required. Directory containing `courses.csv`, `slots.csv`, `hard_conflicts.csv`, `instructor_preferences.csv` and `student_preferences.csv`.

### `--output DIR`

Optional. Destination for `generated_slotbook.csv` and `assignment_explanations.csv`. Defaults to `output`.

### `--solver unitime|cpsat`

Optional. `unitime` (default) runs UniTime cpsolver; `cpsat` runs the v1 OR-Tools CP-SAT model.

### `--time-limit SECONDS`

Optional. Positive search time limit for either solver. Defaults to 10. UniTime always uses the whole limit and keeps the best timetable it found. CP-SAT stops early once it proves optimality.

### `--help`

Print command usage without running a solver.

## INPUT FILES

### `courses.csv`

| Column | Meaning |
|---|---|
| `course_id` | Stable unique identifier |
| `title` | Display title |
| `semester_group` | Cohort or curriculum group |
| `course_type` | Core/elective classification |
| `instructor` | Instructor identifier/name |
| `class_size` | Expected enrolment |
| `historical_slot` | Previous slot; used only as a soft stability preference. Must exist in `slots.csv` |
| `fixed_slot` | Mandatory administrative assignment, or empty |
| `allowed_slots` | Pipe-separated lecture slots such as `A|B|C` |
| `additional_slot` | Fixed lab slot (category `LAB`), or empty |
| `room` | Room used by the lecture; courses sharing a room cannot overlap. Empty means no room rule |

### `slots.csv`

| Column | Meaning |
|---|---|
| `slot_id` | Slot letter |
| `days` | Pipe-separated weekdays, e.g. `MON|TUE|THU|FRI` |
| `start_times` | Pipe-separated `HH:MM`, one per day |
| `lengths` | Optional pipe-separated minutes, one per day; empty means 50 |
| `category` | `LECTURE` or `LAB`; labs cannot be lecture slots |

`data/feasible/slots.csv` reproduces the official grid for B.Tech (other than first year) and M.A.:

- lecture slots A–G, 50 minutes each;
- afternoon slots H, J, K, L and M, with 75-minute periods;
- lab slots P–T, 14:00–16:45, Monday to Friday.

The M.Tech/M.Sc./M.S./Ph.D. grid moves the fifth morning period from 13:00 to 12:00. Use a separate `slots.csv` to model it.

### `hard_conflicts.csv`

Course pairs that may not overlap at any time (lectures and labs), with a human-readable reason.

### `instructor_preferences.csv`

Non-negative soft penalties per instructor and slot. Zero is most preferred. Each (instructor, slot) pair may appear once.

### `student_preferences.csv`

Each row is one synthetic/anonymised student-course selection. A pair's weight is the sum, over students choosing both courses, of `max(1, 4 − worse rank)`.

## OUTPUT

- `generated_slotbook.csv`: course details plus assigned slot.
- `assignment_explanations.csv`: reasons and the classification of every alternative lecture slot.
- CLI diagnostics:
  - UniTime: iterations, when the best timetable was found, and its time-preference and student-conflict values.
  - CP-SAT: status, branches explored and conflicts encountered.

## EXAMPLES

```bash
# Feasible demonstration with UniTime
mvn exec:java -Dexec.args="--input data/feasible --output output/demo"

# The same dataset solved exactly by CP-SAT, for comparison
mvn exec:java -Dexec.args="--input data/feasible --output output/demo-cpsat --solver cpsat"

# Shorter UniTime run
mvn exec:java -Dexec.args="--input data/feasible --time-limit 2"

# Expected validation failure
mvn exec:java -Dexec.args="--input data/infeasible-fixed-conflict --output output/invalid"

# Replay a generated test scenario (after mvn test)
mvn exec:java -Dexec.args="--input target/generated-scenarios/random-3"
```

## EXIT STATUS

- `0`: help shown, or a timetable was exported.
- `1`: input was invalid, or no complete timetable was produced.
- `2`: invocation, parsing or unexpected runtime error.

## LIMITATIONS

- UniTime is a heuristic. It reports `FEASIBLE`, never `OPTIMAL`; use `--solver cpsat` when proof of optimality matters.
- UniTime results depend on how many iterations fit in the time limit, so two runs on different machines can differ. Tests use an iteration cap so their results are repeatable.
- Rooms are fixed per course and treated as large enough. Room capacity, room choice and lab rooms are not modelled.
- Alternative-slot penalties are local estimates, not a complete re-optimisation for every alternative.
- Mock student and instructor preferences are demonstration fixtures, not institutional records.
