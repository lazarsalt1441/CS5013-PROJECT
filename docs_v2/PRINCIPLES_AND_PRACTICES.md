# Engineering Principles and Practices (v2)

## 1. Prompt Engineering

We treat prompts as versioned engineering inputs. Each substantial prompt is recorded with CoSTAR: Context, Objective, Style, Tone, Audience and Response. Prompts state acceptance checks and negative constraints. For v2 these included "do not drop the v1 solver without a way to compare", "do not invent IITM slot times; take them from the official grid", and "do not let UniTime's objective drift from the documented penalty". Before accepting output we review specificity, assumptions, licensing, architectural fit, testability and failure modes. The prompt and its review are committed alongside the code they influenced.

## 2. Structured Debugging

Our debugging cycle is reproduce, observe, hypothesize, isolate, correct, test, regress and document. Running two solvers gives v2 a strong isolation tool. If UniTime and CP-SAT disagree on a small dataset, the fault is in the mapping, not the search. While building the wrapper, UniTime reported 12 for a timetable that cost 20. The breakdown isolated the gap to the time-preference part. Reading cpsolver showed that it scores preferences relative to each class's cheapest time. The fix added that constant back, and a test now pins the equivalence. A second fault was results that changed between test runs. It was traced to Java's per-run randomised map ordering feeding constraint order. See [DEBUGGING.md](DEBUGGING.md).

## 3. Version Control

Git commits represent coherent verified increments. The v2 migration was split into separate commits, one per coherent step:

1. move v1 docs;
2. complete the slot grid;
3. shared time model and validation;
4. CP-SAT overlap rules;
5. the cpsolver dependency;
6. the UniTime wrapper;
7. solver selection;
8. solver comparison tests;
9. a determinism fix;
10. UniTime's stock search moves;
11. v2 docs.

Generated outputs, IDE files and secrets are ignored. Prompts are stored under `prompts/`.

## 4. Testing, Refactoring and Documentation

Fifty-two automated tests check observable guarantees, never one fragile timetable. The checks cover:

- inputs: CSV quoting, fixture loading, validation of good and bad input;
- shared rules: real-time overlap, own-lab, room and lab clashes;
- each solver: completeness, allowed and fixed slots, hard conflicts, explanations and repeatability;
- cross-checks: on 14 generated scenarios, both solvers are compared with an independent penalty calculator, a planted feasible timetable and, for small cases, a brute-force optimum.

The main refactoring was giving overlap and cost a single definition each, `TimeGrid` and `Objective`. That is what let a second solver be added without the rules diverging. Package boundaries keep cpsolver confined to `solver.unitime` and OR-Tools to `OrToolsTimetableSolver`. The repository has a man-page-style manual, an architecture page, a code walkthrough, a test plan, a debugging guide, a verification record and a two-minute script for each version.

## Appendix A: Sample Reviewed Prompts

### Wrapper prompt

**Context:** Java 17 IITM slotbook with a CP-SAT solver, CSV inputs and deterministic explanations.

**Objective:** Make UniTime's course timetabling engine the default solver while keeping the use case: CSV inputs, slot letters, explanations, CLI, JavaFX and exports.

**Response:** A mapping from slotbook concepts to cpsolver constructs, a solver selection switch, and tests that compare both solvers.

**Negative constraints:**
- Do not require a UniTime server or database.
- Do not change the meaning of the objective.
- Do not assert optimality for a heuristic.

### Slot grid prompt

**Context:** `slots.csv` lists A–G with start times only, and labs P/T/R are referenced but undefined.

**Objective:** Reproduce the official IITM grid with real durations and lab blocks.

**Response:** The completed `slots.csv`, with the source cited.

**Negative constraints:**
- Do not guess times; parse the official page.
- Note the M.Tech variant instead of mixing the two grids.

Full prompts, reviews, acceptance decisions and debugging records are in `prompts/PROMPT_LOG.md`.
