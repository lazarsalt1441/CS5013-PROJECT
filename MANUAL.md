# IITM Explainable Slotbook Manual (v3)

This file mirrors [docs_v3/MANUAL.md](docs_v3/MANUAL.md). Earlier manuals: [v2](docs_v2/MANUAL.md), [v1](docs_v1/MANUAL.md). The command-line reference is also a man page: `man ./man/slotbook.1` ([text rendering](man/slotbook.1.txt)).

## ABOUT

`slotbook` turns course, slot, conflict and preference data into a weekly slotbook for an IITM department, and explains every assignment. A web portal collects the preferences from students and professors. v3 adds prerequisite awareness: professors list prerequisites, students upload their grade card, and both the portal and the CLI warn when a student ranks a course whose prerequisite they have not completed. Warnings inform; they never block a submission or change the timetable. See [ABOUT.md](docs_v3/ABOUT.md).

## NAME

`slotbook` - generate and explain an IITM-style course slotbook from CSV constraints.

## SYNOPSIS

```bash
mvn clean test
mvn exec:java -Dexec.args="--input DIR [--output DIR] [--solver unitime|cpsat] [--time-limit SECONDS]
                           [--prerequisites FILE] [--completed-courses FILE] [--no-prerequisite-check]"
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

If prerequisite rules are available, each student's ranked courses are checked against the courses they have completed (see PREREQUISITE WARNINGS). This runs beside the solver and does not affect it.

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

### `--prerequisites FILE` (v3)

Optional. Prerequisite rules (`course_id,prereq_course_id`). Overrides `DIR/prerequisites.csv`. The file must exist.

### `--completed-courses FILE` (v3)

Optional. Completed courses (`student_id,course_id,grade[,term][,source]`). Overrides `DIR/completed_courses.csv`. The file must exist.

### `--no-prerequisite-check` (v3)

Optional. Skip prerequisite warnings even when the files are present. The solve is identical either way.

### `--help`

Print command usage without running a solver.

Unknown options are an error with exit status 2. In v2 they were silently ignored, which hid typos such as `--time-limt`.

## PORTAL OPTIONS (environment)

The preference portal (`portal/`) is configured through environment variables in Vercel or a local shell:

| Variable | Required | Meaning |
|---|---|---|
| `DATABASE_URL` | yes | Postgres connection string. On Neon, use the pooled string (host contains `-pooler`). TLS is used for any non-local host. |
| `ADMIN_PASSWORD` | yes, for admin | Password for `/admin`, compared in constant time. |
| `SOLVER_API_URL`, `SOLVER_API_KEY` | no | HTTP endpoint wrapping the Java CLI; without them the admin uses the manual CSV workflow. |
| `COMPLETION_SOURCE` | no | Where completed courses come from. Default `grade_card`; `admin` is reserved for academic-section records. |
| `TEST_DATABASE_URL` | tests only | Throwaway Postgres for `npm run test:integration`; its `public` schema is dropped. |

| Script | Purpose |
|---|---|
| `npm run db:migrate` | Apply pending `db/migrations/*.sql` to `DATABASE_URL` (safe to re-run) |
| `npm run dev` | Local server on port 4173 (or `PORT`) with the real API handlers |
| `npm test` | 57 unit and contract tests; no network (Node 22+) |
| `npm run test:stress` | 14 stress tests (fuzzing, volume, Postgres concurrency); the 5 Postgres ones need `TEST_DATABASE_URL`. Replay a failure with `STRESS_SEED=<seed>` |
| `npm run test:integration` | 4 tests on real Postgres |
| `npm run check` | Static checks |

Setup and deployment are in the [portal README](portal/README.md).

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

### `prerequisites.csv` (optional, v3)

| Column | Meaning |
|---|---|
| `course_id` | Course with a prerequisite |
| `prereq_course_id` | Course expected to be completed first; may be outside this term's `courses.csv` |

A course listed as its own prerequisite produces a `SELF_PREREQUISITE` warning and is ignored.

### `completed_courses.csv` (optional, v3)

| Column | Meaning |
|---|---|
| `student_id` | Matches `student_preferences.csv` |
| `course_id` | A course on the student's record |
| `grade` | U, W and I do **not** complete a course; any other grade does |
| `term` | Optional, e.g. `05` |
| `source` | Optional: `grade_card` (default; student upload, unverified) or `admin` (reserved) |

Both files are produced by the portal's admin page.

## PREREQUISITE WARNINGS

| Code | Meaning |
|---|---|
| `MISSING_PREREQUISITE` | A ranked course lists a prerequisite the student has not completed; one row per course and prerequisite |
| `NO_COMPLETION_RECORD` | The student ranked courses with prerequisites but has no completion rows; one row per student, with those courses joined by `|` |
| `SELF_PREREQUISITE` | A rule names a course as its own prerequisite; the rule is ignored |

A retaken course counts as completed if any attempt passed. The portal shows the same warnings, with the same codes and messages, when a student submits and on the admin dashboard. The portal also has three grade-card warnings: `GRADE_CARD_UNREADABLE`, `ROLL_NUMBER_MISMATCH` and `GRADE_CARD_ROWS_SKIPPED`.

## OUTPUT

- `generated_slotbook.csv`: course details plus assigned slot.
- `assignment_explanations.csv`: reasons and the classification of every alternative lecture slot.
- `prerequisite_warnings.csv` (v3): `student_id,code,course_id,prereq_course_id,message`, written only when prerequisite rules were supplied. A header-only file means the check ran and found nothing.
- CLI summary (v3): `Prerequisite warnings: N (advisory; the solver input is unchanged)`, followed by one `Warning [CODE] student: message` line per warning.
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

# Prerequisite warnings: same slotbook as data/feasible, plus 22 warnings
mvn exec:java -Dexec.args="--input data/prerequisite-warnings --output output/prereq"

# Files downloaded from the portal admin page
mvn exec:java -Dexec.args="--input data/feasible --prerequisites ~/Downloads/prerequisites.csv --completed-courses ~/Downloads/completed_courses.csv"

# Solver web service for the portal's "Generate slotbook" button (see portal/README.md)
mvn -q exec:java -Dexec.mainClass=in.ac.iitm.slotbook.server.SolverServer   # port 8080
docker run -p 8080:8080 -e SOLVER_API_KEY=secret IMAGE serve

# Live demo: merge portal downloads (in ~/Downloads) into data/feasible and solve
sh scripts/live-demo.sh

# Replay a generated test scenario (after mvn test)
mvn exec:java -Dexec.args="--input target/generated-scenarios/random-3"
```

## EXIT STATUS

- `0`: help shown, or a timetable was exported.
- `1`: input was invalid, or no complete timetable was produced.
- `2`: invocation, parsing or unexpected runtime error, including an unknown option or a named prerequisite file that does not exist.

Prerequisite warnings never change the exit status.

## LIMITATIONS

- UniTime is a heuristic. It reports `FEASIBLE`, never `OPTIMAL`; use `--solver cpsat` when proof of optimality matters.
- UniTime results depend on how many iterations fit in the time limit, so two runs on different machines can differ. Tests use an iteration cap so their results are repeatable.
- Rooms are fixed per course and treated as large enough. Room capacity, room choice and lab rooms are not modelled.
- Alternative-slot penalties are local estimates, not a complete re-optimisation for every alternative.
- Mock student and instructor preferences are demonstration fixtures, not institutional records.
- Prerequisites are advisory. A student-uploaded grade card is not verified; the `admin` completion source is the planned verified path.
- The grade-card parser reads the IITM PDF layout (term, course, title, credit, grade, attendance, year). Scans and photos have no text layer and are reported as unreadable.
- The JavaFX interface writes `prerequisite_warnings.csv` on export but does not yet show warnings on screen.
- The portal still has no student or professor authentication; do not collect real grade cards until IITM login, consent and retention rules exist.
