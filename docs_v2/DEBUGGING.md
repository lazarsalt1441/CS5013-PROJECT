# Structured Debugging Practice (v2)

1. **Reproduce:** Reduce the failure to one command or the smallest CSV fixture.
2. **Observe:** Capture the status, stack trace, input row, solver diagnostics and expected behaviour.
3. **Hypothesize:** List plausible causes before editing code.
4. **Isolate:** Work out which layer owns the fault: parsing, validation, `TimeGrid`, the UniTime mapping, UniTime's search, CP-SAT, explanation, export or UI.
5. **Correct:** Make the smallest change at the owning boundary.
6. **Test:** Add or update a focused regression test.
7. **Regress:** Run the complete suite and the feasible/infeasible CLI demonstrations with both solvers.
8. **Document:** Record non-trivial failures here or in the prompt log, and commit the evidence with the fix.

## Tools for the wrapper

- **Compare solvers.** Run the same dataset with `--solver unitime` and `--solver cpsat`. If both give the same objective, the mapping is consistent; a difference on a small dataset points to the mapping, not the search.
- **Replay a scenario.** After `mvn test`, every generated dataset is in `target/generated-scenarios/<name>/`, and the CLI can run it directly.
- **Turn up cpsolver logging.** `UniTimeTimetableSolver` sets `org.cpsolver` to `WARN`. Change it to `INFO` locally to see each search phase.
- **Read the diagnostics.** The UniTime run reports when the best timetable was found. If that is at iteration 23 of 1.3 million, a shorter `--time-limit` gives the same answer.

## Examples from building v2

### 1. NullPointerException inside UniTime's `RoomConstraint`

- **Reproduce:** `UniTimeTimetableSolverTest` crashed while building the room constraint's context.
- **Observe:** `Model.getCriterion(...)` returned null inside cpsolver.
- **Hypothesize:** The wrapper had passed `General.Criteria` listing only the two criteria it uses. cpsolver's constraints assume UniTime's default criteria are registered.
- **Correct:** Keep the default criteria list and set unused weights to zero through `Weight.<Criterion>` properties.
- **Test:** The same test then ran to completion.

### 2. UniTime reported 12 where the timetable cost 20

- **Observe:** UniTime's timetable cost exactly the CP-SAT optimum of 20 when recomputed by `PenaltyCalculator`, but the criteria said 12. The breakdown showed the student part matched (1 × 3 = 3). The time part was 9 instead of 17.
- **Isolate:** cpsolver's `Placement.getTimePenalty()` subtracts each lecture's cheapest time preference. Penalties are measured relative to the best slot, so a constant per course is missing. The search is unaffected because a constant does not change which timetable is best.
- **Correct:** Add the per-course minimum back when reporting.
- **Test:** `uniTimeObjectiveEqualsTheSlotbookPenalty` keeps this from regressing.

### 3. Test results changed between runs

- **Observe:** A scenario passed in one `mvn test` and failed in the next. Within one JVM, the same solve was repeatable.
- **Hypothesize:** Something depends on per-JVM state. The planted penalties changed too, so even CP-SAT's baseline timetable differed between runs.
- **Isolate:** `TimetableProblem.studentPairWeights()` returned `Map.copyOf(...)`. Java randomises the iteration order of such maps on every JVM start. Both solvers add constraints in that order, so ties between equal-cost timetables broke differently each run.
- **Correct:** Return the pairs in sorted order, and use insertion-ordered maps for the CP-SAT instructor and room groups.
- **Test:** Two full runs in separate JVMs now produce identical reports, apart from CP-SAT's time-limited `students-300` result.

### 4. UniTime stalled right after construction

- **Observe:** On `shared-instructors`, UniTime found its best timetable at iteration 133 and then made no progress for 170,000 iterations. It scored 33 against a planted 30 and an optimum of 26.
- **Isolate:** Reading UniTime's `CourseTimetablingSolverService` and its stock solver settings showed what the wrapper was missing. UniTime adds course-specific moves (time and room change, swaps, suggestions) to hill climbing and great deluge, and lowers the hill-climber idle limit to 10,000.
- **Correct:** Use the same moves and limit. UniTime's other shipped algorithm, Default IFS, was also benchmarked; see [TEST_PLAN.md](TEST_PLAN.md#choosing-unitimes-search-algorithm).

### 5. Report sorting crashed on `random-preferences`

- **Observe:** The report ordered `random-N` scenarios by number using `startsWith("random-")`, which also matched `random-preferences`.
- **Correct:** Match `random-\d+`.
- **Lesson:** Build string keys so that their prefixes don't collide.
