# IITM Explainable Slotbook Manual

> **Version 1 (CP-SAT) manual.** For the current version see [docs_v2/MANUAL.md](../docs_v2/MANUAL.md).

## NAME

`slotbook` - generate and explain an IITM-style course slotbook from CSV constraints.

## SYNOPSIS

```bash
mvn clean test
mvn exec:java -Dexec.args="--input data/feasible --output output/demo"
mvn javafx:run
```

## DESCRIPTION

The tool reads five CSV files, validates their references and contradictions, creates a Google OR-Tools CP-SAT model, assigns one primary slot to every course, and minimizes three transparent costs:

1. Instructor preference penalties.
2. A small penalty for moving away from the historical slot.
3. Weighted clashes between courses requested by the same students.

Hard constraints always take precedence. They include exactly one slot per course, allowed/fixed slots, protected course pairs, and instructor non-overlap.

The generated explanation is deterministic. It reports the constraints satisfied by the selected slot, the selected slot's local penalties, and why each alternative is rejected or what its estimated local cost would be. It does not use an LLM to invent reasons.

## OPTIONS

### `--input DIR`

Required. Directory containing `courses.csv`, `slots.csv`, `hard_conflicts.csv`, `instructor_preferences.csv`, and `student_preferences.csv`.

### `--output DIR`

Optional. Destination for `generated_slotbook.csv` and `assignment_explanations.csv`. Defaults to `output`.

### `--help`

Print command usage without running the solver.

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
| `historical_slot` | Previous slot; used only as a soft stability preference |
| `fixed_slot` | Mandatory administrative assignment, or empty |
| `allowed_slots` | Pipe-separated domain such as `A|B|C` |
| `additional_slot` | Preserved lab/tutorial slot, or empty |
| `room` | Existing/requested display room |

### `slots.csv`

Defines slot labels and their weekly patterns. `start_times` correspond positionally to `days`.

### `hard_conflicts.csv`

Defines course pairs that may not share a primary slot and records a human-readable reason.

### `instructor_preferences.csv`

Defines non-negative soft penalties. Zero is most preferred; higher values are less desirable.

### `student_preferences.csv`

Each row is one synthetic/anonymized student-course selection. Pair weights are derived from students selecting both courses, with higher-ranked pairs receiving greater weight.

## OUTPUT

- `generated_slotbook.csv`: course details plus assigned slot.
- `assignment_explanations.csv`: reasons and classification of every alternative slot.
- CLI diagnostics: solve status, objective value, runtime, branches, and solver conflicts.

## EXAMPLES

```bash
# Feasible demonstration
mvn exec:java -Dexec.args="--input data/feasible --output output/demo"

# Expected validation failure
mvn exec:java -Dexec.args="--input data/infeasible-fixed-conflict --output output/invalid"
```

## EXIT STATUS

- `0`: help shown or a feasible/optimal timetable exported.
- `1`: input was valid but no solution was produced, or validation identified infeasibility.
- `2`: invocation, parsing, or unexpected runtime error.

## LIMITATIONS

- Rooms and additional laboratory slots are displayed but not optimized in the mid-evaluation version.
- Alternative-slot penalties are local counterfactual estimates, not a complete re-optimization for every alternative.
- Mock student and instructor preferences are demonstration fixtures, not institutional records.
- Authentication and multi-user preference collection are outside the mid-evaluation scope.
