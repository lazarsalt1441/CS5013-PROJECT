# Engineering Principles and Practices

## 1. Prompt Engineering

We treat prompts as versioned engineering inputs. Each substantial prompt is recorded with CoSTAR: Context, Objective, Style, Tone, Audience, and Response. Prompts explicitly state acceptance checks and negative constraints such as “do not invent institutional data,” “do not couple UI code to solver variables,” and “do not assert a unique timetable when several optima may exist.” Before accepting output, we review specificity, assumptions, privacy, architectural fit, testability, and failure modes. The prompt and its review are committed alongside the influenced code.

## 2. Structured Debugging

Our debugging cycle is reproduce, observe, hypothesize, isolate, correct, test, regress, and document. Failures are reduced to the smallest dataset or command. We classify ownership among parser, validator, solver, explanation, exporter, and UI before editing. For example, an export test initially expected a UI-only heading. Inspection proved the CSV contained the correct semantic evidence, so the test—not production code—was corrected, and all tests were rerun.

## 3. Version Control

Git commits represent coherent verified increments: architecture and prompt governance, mock data, core solver, tests, and evaluator documentation. Generated outputs, IDE files, and secrets are ignored. Prompts are stored under `prompts/`, and non-trivial revisions and rejected assumptions remain visible. We inspect diffs and run the relevant tests before committing.

## 4. Testing, Refactoring, and Documentation

Eleven automated tests cover CSV quoting, fixture loading, student co-demand aggregation, valid/invalid inputs, assignment completeness, allowed/fixed slots, hard conflicts, explanations, infeasibility, and exports. Tests assert invariants rather than one fragile optimized timetable. Package boundaries keep domain, I/O, validation, solver, explanation, service, CLI, UI, and export responsibilities separate. Public concepts have JavaDoc; the repository contains a man-page-style manual, architecture, mathematical walkthrough, test plan, debugging guide, and two-minute presentation script.

## Appendix A: Sample Reviewed Prompts

### Architecture prompt

**Context:** Java 17 IITM course-slotting application for an individual viva. **Objective:** define modular boundaries for parsing, validation, optimization, explanation, UI, export, and testing. **Response:** repository structure and verification plan before solver implementation. **Negative constraints:** do not hide solver logic in controllers; do not claim mock inputs are real; do not use an LLM to invent reasons.

### Testing prompt

**Context:** parser, validator, CP-SAT solver, explanation builder, exporter, CLI, and UI exist. **Objective:** test observable guarantees and important failure paths. **Response:** JUnit tests for parsing, validation, completeness, domains, fixed slots, conflicts, explanations, infeasibility, and exports. **Negative constraints:** do not assert a single exact optimum; do not test private variable names; do not weaken validation to pass tests.

Full prompts, reviews, acceptance decisions, and debugging records are stored in `prompts/PROMPT_LOG.md`.
