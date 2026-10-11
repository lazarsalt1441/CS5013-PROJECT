# Mid Evaluation Runbook

## Before the slot

- Submit `principles_and_practices.pdf` to Moodle.
- Open `architecture.png`, the live portal results page, and the GitHub Actions page.
- Keep a terminal in the repository root and an editor on `InputValidator.java`, `Objective.java`, and `portal/tests/api.test.mjs`.
- Run `node scripts/validate_demo.mjs`, `npm test --prefix portal`, and `node portal/scripts/validate.mjs`.
- If Maven 3.9+ is installed, also run `mvn test`. The CI workflow is the fallback evidence if the evaluation machine lacks Maven.

## Two minute explanation

**0:00-0:20 - Problem.** "IITM slotting must combine curriculum conflicts, real slot times, fixed labs, rooms, instructor preferences, historical stability, and student elective demand. Our contribution is a complete IITM-oriented collection-to-publication workflow around UniTime, with an exact CP-SAT reference and deterministic explanations."

**0:20-0:45 - Collection and validation.** "Students rank courses and professors assign penalties to slots in separate demo forms. Vercel API functions validate submissions and store them through Apps Script in Google Sheets. The admin exports exactly the two CSV schemas consumed by the Java model. The Java validator rejects malformed references and contradictory hard constraints before solving."

**0:45-1:15 - Optimisation.** "The default adapter maps courses, IITM time blocks, rooms, labs, instructors, protected pairs, and co-demand into UniTime cpsolver. OR-Tools CP-SAT implements the same objective as a reference that can prove optimality on small cases. `TimeGrid` defines overlap once, and `Objective` defines penalties once, so both solvers and the tests use the same domain meaning."

**1:15-1:40 - Explainability.** "Explanations are generated from inputs and the final assignment, not invented by an LLM. They state hard constraints satisfied, instructor and historical penalties, student-conflict contribution, and why each alternative is rejected or more costly. In the showcase, eight electives compete for seven slots, so one overlap is unavoidable; UniTime chooses the minimum-weight EE3110/EE5175 pair in B, with contribution 3."

**1:40-2:00 - Evidence.** "CI runs 52 JUnit checks, seven portal API tests, both static validators, both solvers, and uploads generated results. On the showcase, UniTime obtains objective 9 and CP-SAT proves 9 optimal. Prompts are versioned with CoSTAR structure, negative constraints, review decisions, tests, and the commits they influenced."

## Live demonstration

1. Show the architecture image and trace one student submission to the `Students` sheet.
2. Show the five files in `data/demo-showcase` and run `node scripts/validate_demo.mjs`.
3. Open the public result and inspect EE3110.
4. Point to the four retained generated CSVs under `data/demo-showcase/generated`.
5. Show the CI job that regenerates both solver outputs.

## Likely viva questions

### Why UniTime if CP SAT already works

UniTime is the production-grade timetabling engine being evaluated as the default heuristic. CP-SAT remains an independent reference for exact small-case checks. The value of this repository is the IITM data model, collection workflow, validation, mapping, comparison harness, and evidence-backed publication layer.

### Why not use an LLM to explain the answer

The mathematical model is inspectable, so the authoritative explanation should be deterministic. `ExplanationBuilder` derives reasons from constraints, penalties, and the final assignment. An LLM may later rewrite that evidence for different audiences, but it must not invent causes or decide slots.

### What does optimal mean here

CP-SAT may report `OPTIMAL` when it proves no lower objective exists. UniTime is heuristic and reports `FEASIBLE`; matching the CP-SAT optimum on the showcase is evidence for this case, not a universal optimality claim.

### Why is the showcase conflict unavoidable

All 28 pairs among eight electives have positive co-demand, but only seven lecture slots are available. By the pigeonhole principle, at least two electives share a slot. The solver selects the lowest-weight permitted overlap while preserving all hard constraints.

## Live modification drills

### Drill 1 Add a validation rule

Reject `class_size <= 0` in `InputValidator`, add a stable issue code, and assert the code in `InputValidatorTest`. This is the best first drill because ownership is clear and the change is small.

### Drill 2 Change a soft weight

Change `Objective.STUDENT_CONFLICT_MULTIPLIER` from 3 to 4. Explain why both solvers observe the change without editing solver-specific constants, then run the solver comparison tests.

### Drill 3 Add a portal API validation

Reject a student preference list with duplicate course IDs in the submission endpoint and add a Node contract test. Explain why browser validation alone is insufficient.

### Drill 4 Add a result column

Add `semester_group` to the public results table. Trace it from `generated_slotbook.csv`, through Sheets and `/api/results`, to `results.js`. Update the portal validator or API test if the contract changes.

### Drill 5 Add a CLI option

Add `--max-iterations N`, validate that it is positive, pass it through `SlotbookService`, and document it in `MANUAL.md`.

## Safe workflow during the evaluation

1. Restate the requested behaviour.
2. Identify the module that owns it.
3. Run the nearest focused test before editing.
4. Make the smallest coherent change and one focused assertion.
5. Run the focused test, then the relevant full suite.
6. Explain the data flow, failure path, and any trade-off.

