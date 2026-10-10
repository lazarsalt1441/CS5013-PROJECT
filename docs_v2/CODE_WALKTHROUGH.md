# Code Walkthrough (v2)

## End-to-end call path

1. `SlotbookCli` or `SlotbookApplication` receives a dataset directory and a `SolverChoice` (UniTime by default).
2. `SlotbookService` coordinates one run.
3. `DatasetLoader` parses five CSV files into immutable model records.
4. The chosen solver calls `InputValidator`, which checks identifiers, slot times, labs, preferences, ranks, duplicates and direct contradictions.
5. The solver runs:
   - **UniTime (default).** `UniTimeModelBuilder` builds a cpsolver `TimetableModel`. `UniTimeTimetableSolver` runs UniTime's search and maps the best timetable back to slot letters.
   - **CP-SAT.** `OrToolsTimetableSolver` builds and solves the v1 Boolean model, extended with time overlaps, rooms and labs.
6. `ExplanationBuilder` derives reasons and alternative classifications from the problem and the final assignment.
7. `SlotbookExporter` writes the assignment and explanation CSV files.

## Shared rules

Both solvers, the validator, the explanations and the test oracles use two small classes, so they cannot drift apart.

### `TimeGrid`: what counts as a clash

Each slot is expanded into its weekly meetings. `Slot.cells()` turns them into a bitset with one bit per 5 minutes of the week. Two slots overlap exactly when their bitsets intersect. A course's **parts** are its lecture slot plus, if it has one, its fixed lab. Then:

- two courses **clash** when any part of one overlaps any part of the other;
- `partOverlaps` counts the overlapping part pairs, which the student penalty uses;
- a **room clash** compares lecture slots only, because lab rooms are not part of the input.

### `Objective`: what a placement costs

```text
course penalty(c, s) = instructor penalty(c, s)        (default 1)
                     + 2 if s differs from the historical slot

total = sum over courses of course penalty(c, slot(c))
      + sum over student pairs (a, b) of weight(a, b) x partOverlaps(a, b) x 3
```

## UniTime mapping (`UniTimeModelBuilder`)

| Slotbook | UniTime |
|---|---|
| Course | `Lecture` whose `TimeLocation`s are the allowed slots. Fixed slots and slots overlapping the course's own lab are left out |
| Lab | Extra `Lecture` with one `TimeLocation` |
| Instructor | One `InstructorConstraint` per instructor, containing their lectures and labs |
| Room | One `RoomConstraint` per room name; the lecture's single `RoomLocation` |
| Hard conflict | Required `GroupConstraint` `DIFF_TIME` over all parts of both courses |
| Student pair of weight w | w synthetic `Student`s enrolled in all parts of both courses; cpsolver builds `JenrlConstraint`s from them |
| Course penalty | Preference of each `TimeLocation` |
| Historical slot | `Lecture.setInitialAssignment`, so the search starts from last year's timetable |

### Encoding IITM slots in a `TimeLocation`

A UniTime `TimeLocation` has one start time shared by all its days. An IITM slot does not: A meets Monday 08:00, Tuesday 13:00, Thursday 11:00 and Friday 10:00. UniTime decides overlap as:

```text
shareDays && shareHours && shareWeeks      (weeks = intersecting week bitsets)
```

So every slot gets the same day code, start and length, which makes the first two tests always true. The slot's real meetings go into the week bitset as `Slot.cells()`. UniTime's room, instructor, distribution and student checks all reduce to "do the week bitsets intersect", which is exactly IITM overlap. Each slot also gets its own date-pattern id, so UniTime treats different slots as different times.

### Why UniTime's objective equals the slotbook objective

- Only two UniTime criteria carry weight:
  - `TimePreferences`, with weight 1;
  - `StudentOverlapConflict`, with weight 3.

  Every other default criterion stays registered, because its constraints expect it, but has weight 0.
- `StudentOverlapConflict` adds up, over overlapping lecture pairs, the number of students they share. With w synthetic students per pair, that sum is weight × overlapping parts.
- `TimePreferences` scores each placement relative to the cheapest time in that lecture's domain. This leaves out a constant per course, so the wrapper adds `min preference` back for each course.

`UniTimeTimetableSolverTest` and `GeneratedScenarioTest` check that the reported objective equals the independent `PenaltyCalculator` on every scenario.

### Search (`UniTimeTimetableSolver`)

| Property | Value | Meaning |
|---|---|---|
| `Neighbour.Class` | `SimpleSearch` | Construction, then hill climbing, then great deluge (UniTime's "Deluge" algorithm) |
| `Search.GreatDeluge` | `true` | Great deluge rather than simulated annealing |
| `HillClimber.MaxIdle` | 10000 | UniTime's stock idle limit before moving to great deluge |
| `*.AdditionalNeighbours` | `TimeChange`, `RoomChange`, `TimeSwap@0.01`, `RoomSwap@0.01`, `Suggestion@0.01` | UniTime's stock course timetabling moves |
| `Value.Class` / `Variable.Class` | `PlacementSelection` / `LectureSelection` | UniTime's course timetabling heuristics |
| `Extensions.Classes` | `ConflictStatistics` | Remembers past conflicts and avoids repeating them |
| `Termination.TimeOut` | `--time-limit` | Wall-clock limit |
| `Termination.MaxIters` | -1, or a cap in tests | A cap makes runs repeatable |
| `General.Seed` | 5013 | Fixed random seed |

Why Deluge and not UniTime's Default IFS algorithm: see the benchmark in [TEST_PLAN.md](TEST_PLAN.md#choosing-unitimes-search-algorithm).

Afterwards the best timetable is restored. If every course is placed the status is `FEASIBLE`. Otherwise it is `INFEASIBLE`, with diagnostics naming each unplaced course and the constraints that block each of its placements.

## CP-SAT model (v1, extended)

For course `c` and slot `s`, the binary `x[c,s]` is one exactly when that assignment is chosen.

```text
sum_s x[c,s] = 1                              exactly one lecture slot
x[c,s] = 0 if s not allowed, not fixed, or overlapping c's own lab
not x[a,s] or not x[b,t]                      when (a in s) and (b in t) clash:
                                                hard conflict, same instructor, or same room
y[a,s,b,t] >= x[a,s] + x[b,t] - 1             student clash indicator
minimise  sum course penalty(c,s) x[c,s] + sum weight(a,b) x partOverlaps x 3 x y
```

## Explanation logic

The explanation module reads only the problem and the final assignment, so it explains UniTime and CP-SAT timetables the same way. For each alternative lecture slot it checks, in order:

1. Is the slot allowed?
2. Does it contradict a fixed assignment?
3. Does it overlap the course's own lab?
4. Would it overlap a hard-conflict partner, labs included?
5. Would it double-book the instructor?
6. Would it double-book the room?
7. If feasible, what local instructor, stability and student-conflict cost would it have?

A feasible alternative's cost is labelled a local estimate, not a claim that the whole timetable would stay optimal.

## Safe live modifications

- New CSV validation: modify `InputValidator` and add a focused test.
- New hard relation: express it through `TimeGrid` if it is about overlap. Add it in `OrToolsTimetableSolver` and as a UniTime constraint in `UniTimeModelBuilder`, then test the invariant through `ConstraintChecker`.
- Changed weight: edit `Objective`. Both solvers and the explanations follow, and `UniTimeTimetableSolver` reads the student multiplier from it.
- New output column: update `SlotbookExporter` and its export test.
- New UI field: update only `SlotbookApplication` or its view record.
