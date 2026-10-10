# Prompt Log and Review Register

Every substantial AI-assisted change is recorded before implementation. Each entry contains the prompt, its CoSTAR structure, negative constraints, review notes, accepted changes, and verification evidence. Prompt entries are committed with the code they influenced.

From P006 onward each entry also keeps the **original prompt** word for word as it was typed, with only line breaks joined. The CoSTAR block is the structured restatement used for review. Negative constraints are marked as either stated in the prompt, agreed during the planning phase, or added at review, so the log shows which came from where.

| ID | Change | Status | Commit |
|---|---|---|---|
| P001 | Establish project architecture and engineering workflow | Accepted | `c6728e6` |
| P002 | Create auditable mock slotbook and student-preference fixtures | Accepted | `70f4ba6` |
| P003 | Implement parsing, validation, CP-SAT optimization, and explanations | Accepted | `11eb6ad` |
| P004 | Test feasible, infeasible, parsing, explanation, and export behavior | Accepted | `11eb6ad` |
| P005 | Produce evaluator-facing manuals and practices document | Accepted | current documentation commit |
| P006 | Replace the default solver with a UniTime (cpsolver) wrapper; real IITM times, rooms and labs; docs_v1 / docs_v2 | Accepted | `cfbe4f5`..`3c9d6cb` |
| P007 | Audit the repository and GitHub workflow before further changes | Accepted | no code change |
| P008 | Explain the timetable pipeline and publish the file-structure tree | Accepted | `7abd006` |
| P009 | Build a generated-scenario testing framework and penalty report | Accepted | `efdf10d`..`e4e7ca9` |
| P010 | Commit policy: one commit per coherent change | Accepted | applied from `efdf10d` |
| P011 | Install the toolchain and run the tests and CLI locally | Accepted | no code change |
| P012 | Compare the project with UniTime and list improvements | Accepted | analysis; led to P006 |
| P013 | Summarise the UniTime vs CP-SAT comparison in the READMEs | Accepted | `001b602` |
| P014 | Record this session's prompts in this log | Accepted | this commit |

P006–P014 were all written on 10 October 2026. In order of use they ran P007, P008, P009, P010, P011, P012, P006, P013, P014. P006 keeps its number because commit `3c9d6cb` refers to it.

## P001 - Project architecture and workflow

### CoSTAR prompt

**Context:** We are building a Java 17 IITM course-slotting application for a CS5013 mid-evaluation. The product must be understandable during an individual viva and must preserve a traceable AI-assisted engineering process.

**Objective:** Define a modular Maven project with explicit boundaries for parsing, validation, optimization, explanation, UI, export, and testing.

**Style:** Use small cohesive Java classes, immutable records where practical, descriptive names, and documentation aimed at a student who must explain every decision.

**Tone:** Precise, engineering-focused, and honest about synthetic data and limitations.

**Audience:** Course TAs, stakeholder, and team members maintaining the code.

**Response:** Provide the repository structure, build dependencies, architecture flow, and verification plan before implementing solver logic.

### Negative prompt / constraints

- Do not hide solver logic inside JavaFX controllers.
- Do not claim mock preferences are real IITM data.
- Do not use an LLM to invent assignment reasons.
- Do not add authentication, room optimization, or production deployment to the mid-evaluation scope.
- Do not commit generated build artifacts or secrets.

### Prompt review

- **Specificity:** Pass - language, modules, evaluation context, and exclusions are explicit.
- **Verifiability:** Pass - boundaries can be checked from package imports and tests.
- **Risk:** Low - no external side effects or private data.
- **Revision:** Added the requirement that explanations remain deterministic and evidence-backed.

### Acceptance and verification

- Accept a Maven skeleton containing Java 17, OR-Tools, JavaFX, and JUnit.
- Accept an architecture document with a one-way data flow.
- Verify that the project compiles before solver implementation is committed.

## P002 - Mock data and preference fixtures

### CoSTAR prompt

**Context:** The supplied EE slotbook contains historical course assignments but does not contain the private reasoning, student selections, or instructor constraints that produced them.

**Objective:** Create a small dataset that resembles the EE slotbook and supports hard conflicts, instructor penalties, historical stability, and student co-demand objectives.

**Style:** Human-readable CSV with stable identifiers and enough variety to exercise feasible and infeasible solver paths.

**Tone:** Transparent about provenance and suitable for a classroom demonstration.

**Audience:** Evaluators inspecting whether the application is solving a meaningful constraint problem.

**Response:** Produce a feasible 18-course fixture, 30 synthetic student preference profiles, and an infeasible fixed-conflict fixture.

### Negative prompt / constraints

- Do not represent synthetic student IDs or preferences as real IITM data.
- Do not include names, roll numbers, emails, or other personal student data.
- Do not copy the supplied historical slot as a hard assignment except for explicitly fixed courses.
- Do not create an infeasible default demonstration dataset.
- Do not add constraints that cannot be explained in plain language.

### Prompt review

- **Specificity:** Pass - scale, files, objectives, and infeasible case are defined.
- **Privacy:** Pass - identifiers S001-S030 are synthetic and contain no personal information.
- **Bias check:** Historical slots have a small stability cost but do not determine the answer.
- **Revision:** Added a separate infeasible fixture instead of corrupting the feasible fixture at runtime.

### Acceptance and verification

- Every course references known slots.
- Every hard-conflict endpoint references a known course.
- Every student selection references a known course.
- The feasible fixture must solve; the infeasible fixture must be rejected with a specific fixed-conflict diagnostic.

## P003 - Core implementation

### CoSTAR prompt

**Context:** Five CSV files describe courses, IITM slots, hard conflicts, instructor penalties, and synthetic student selections. Explanations must be produced from solver evidence.

**Objective:** Implement a Java 17 pipeline that parses and validates the files, formulates a CP-SAT model, minimizes transparent soft penalties, and returns assignment evidence to CLI and JavaFX clients.

**Style:** Immutable domain records, dependency direction matching the architecture document, small methods, and descriptive constraint names.

**Tone:** Deterministic and auditable rather than promotional.

**Audience:** Students modifying the project live and evaluators tracing each result back to code.

**Response:** Implement domain, I/O, validation, solver, explanation, export, CLI, and a minimal JavaFX interface. Include JavaDoc on public concepts.

### Negative prompt / constraints

- Do not ask an LLM to decide or explain a slot.
- Do not continue after validation errors.
- Do not silently ignore unknown courses or slots.
- Do not couple JavaFX controls to CP-SAT variables.
- Do not claim global optimality when CP-SAT returns only FEASIBLE or UNKNOWN.
- Do not expose private student identities; fixtures use synthetic IDs.

### Prompt review

- **Correctness risk:** Student pair clashes require an AND linearization; require all three linking inequalities.
- **Explainability risk:** Alternative costs are labelled estimated local penalties, not full re-optimization results.
- **Maintainability:** CLI and UI share one application service.
- **Revision:** Added deterministic CP-SAT seed and one worker to make demonstrations reproducible.

### Acceptance and verification

- Compile all production sources on Java 17.
- Feasible data returns a complete assignment and two CSV exports.
- Fixed conflicting courses return INVALID_INPUT with code FIXED_CONFLICT.
- Every assigned slot is allowed and every hard-conflict pair is separated.
- Explanations name the assigned slot and classify every alternative slot.

## P004 - Test and refactor review

### CoSTAR prompt

**Context:** The core application now has parser, validator, CP-SAT solver, explanation builder, CLI, exporter, and UI boundaries.

**Objective:** Add automated tests that prove the documented hard guarantees and important failure paths instead of merely increasing line coverage.

**Style:** Small JUnit 5 tests named after observable behavior, sharing a solved fixture only when repeated optimization would be wasteful.

**Tone:** Skeptical: try to falsify solver and explanation claims.

**Audience:** Developers making live changes and evaluators checking regression protection.

**Response:** Test quoted CSV parsing, fixture counts, preference aggregation, validation, completeness, allowed slots, fixed slots, hard conflicts, explanations, infeasibility, and export files.

### Negative prompt / constraints

- Do not assert one exact optimized timetable when multiple optimal solutions may exist.
- Do not test private implementation details such as CP-SAT variable names.
- Do not treat successful compilation as proof of constraint correctness.
- Do not omit negative and malformed-input behavior.
- Do not weaken production validation to make tests pass.

### Prompt review

- **Test quality:** Assertions target invariants rather than a fragile exact assignment.
- **Coverage:** Includes parser, data aggregation, validator, solver, explanation, and exporter.
- **Missing item noted:** GUI interaction is kept out of headless unit tests and covered by a manual smoke test.

### Acceptance and verification

- All JUnit tests pass from a clean build.
- CLI produces both expected export files.
- The infeasible fixture reports FIXED_CONFLICT.

### Debugging record

- **Observed failure:** The exporter test searched for the presentation heading `Alternative slots`, but CSV stores alternatives as structured column content without that UI-only heading.
- **Hypotheses checked:** Export failure, empty explanation map, or an incorrect test expectation.
- **Evidence:** Both files existed and the explanation CSV contained course IDs and individual alternative descriptions.
- **Root cause:** The test asserted formatting owned by `AssignmentExplanation.asText()` against output owned by `SlotbookExporter`.
- **Fix:** Assert the exported semantic marker `rejected because` instead of an unrelated presentation heading.
- **Regression protection:** Re-run the complete test suite, not only the previously failing test.

## P005 - Evaluator documentation

### CoSTAR prompt

**Context:** The mid-evaluation requires a three-page engineering-practices PDF, architecture soft copy, two-minute system explanation, and code comprehension during a live modification.

**Objective:** Document how the tool works, how to run it, how each module owns behaviour, what was tested, and how AI-assisted work was reviewed.

**Style:** Concise evaluator-facing prose, exact command examples, diagrams for data flow, and explicit limitations.

**Tone:** Evidence-based and non-defensive.

**Audience:** CS5013 TAs evaluating engineering practice and individual understanding.

**Response:** Create a man-page-style manual, about page, code walkthrough, test plan, debugging guide, architecture SVG, two-minute script, source markdown, and a visually verified three-page PDF.

### Negative prompt / constraints

- Do not exceed three PDF pages.
- Do not hide limitations or synthetic-data provenance.
- Do not include test claims that were not executed.
- Do not describe an LLM as part of the mathematical decision path.
- Do not write documentation that merely repeats class names without explaining data flow.

### Prompt review and verification

- PDF page count checked with `pdfinfo`: exactly three A4 pages.
- All three pages rendered to PNG and visually inspected for clipping and overlap.
- Commands, options, input schemas, outputs, exit statuses, mathematical constraints, and safe live-edit locations documented.
- Test counts match the executed JUnit report.

## P006 - UniTime wrapper (v2)

### Original prompt

> remove all the solving from this , and make it a wrapper around UNITIME Instead , such that it makes use of the optimisations done in that while still holding the use case i want rename the documentation of the previous thing to docs_v1 and make seperate similarly documented docs_v2 for the wrapper version
>
> be in plan mode -- ask me all the questions you have about it , then convert to auto mode to build

The prompt deliberately asked for a planning phase first, so that design decisions were made by the developer before any code was written.

### CoSTAR prompt

**Context:** v1 solves the slotbook with its own OR-Tools CP-SAT model. UniTime is an open-source university timetabling system. Its solver library, cpsolver, is on Maven Central and implements competition-proven course timetabling search.

**Objective:** Remove the solving from this project and make it a wrapper around UniTime, so that it uses UniTime's optimisation while keeping the use case: CSV inputs, IITM slot letters, explanations, CLI, JavaFX and exports. Move the previous documentation to `docs_v1` and write a matching `docs_v2`.

**Style:** Same layering as v1. All UniTime-specific code lives in one package. The rules for overlap and cost each have one definition, shared by every component.

**Tone:** Honest about heuristics: no optimality claims for UniTime.

**Audience:** Evaluators comparing v1 and v2, and students making live changes.

**Response:** A mapping from slotbook concepts to cpsolver constructs, a solver switch, tests comparing both solvers, and v2 documentation.

### Planning phase: questions and the developer's answers

| Question | Options offered | Answer |
|---|---|---|
| How should the app wrap UniTime? | Embed cpsolver; embed and export UniTime XML; run the full UniTime server | **Embed cpsolver** as a Maven library, with no server or database |
| What happens to the v1 CP-SAT code? | Delete and tag `v1`; delete; keep both | **Keep both solvers** behind one interface, so results can be compared |
| How much of UniTime's model to use? | Real times and rooms; real times only; same as v1 | **Real times and rooms** |
| How should tests adapt to a heuristic? | Feasibility and gap report; strict optimality; feasibility only | **Feasibility plus gap report** |
| Where do lab-slot times come from? | Standard IITM chart; developer supplies; skip labs | **Search online for the official IITM timetable and extract the full grid** |
| What moves into `docs_v1`? | `docs/` plus README and MANUAL; `docs/` only | **`docs/` plus README and MANUAL** |
| Which solver is the default? | UniTime; CP-SAT | **UniTime**, with `--solver cpsat` available |

Two answers went further than the options offered. Choosing "keep both solvers" over "remove all the solving" turned CP-SAT into an exact reference for testing UniTime. "Search online for the official timetable" replaced assumed lab times with the real grid.

### Negative prompt / constraints

Agreed during planning:

- Do not require a UniTime server or database.
- Do not invent slot times. Parse <https://www.iitm.ac.in/timetable> and cite it.

Added at review:

- Do not change what the objective means. UniTime's score must equal the documented penalty.
- Do not assert that a heuristic is optimal.
- Do not mix the B.Tech and M.Tech grids; document the difference.
- Do not let the explanations depend on solver internals.

### Prompt review

- **Correctness risk:** A UniTime `TimeLocation` has one start time for all days, but IITM slots don't. Resolved by encoding the real meetings in the week bitset, which UniTime's overlap test already checks.
- **Equivalence risk:** UniTime's student criterion counts students. Resolved with one synthetic student per unit of pair weight. Its time preference is relative to each class's best time; resolved by adding the per-class minimum back.
- **Scope change:** Rooms and labs became hard rules in both solvers. This removed a real room clash in the v1 output and raised the baseline from 17 to 20.
- **Revision:** UniTime's stock moves and hill-climber limit were added after the search stalled. Deluge was chosen over UniTime's Default IFS after benchmarking both.

### Acceptance and verification

- 52 JUnit tests pass, with identical reports across separate JVM runs.
- On `data/feasible`, UniTime and CP-SAT both give 20, the proven optimum, and EE1100 and EE1101 no longer share CRC.
- On every generated scenario, both solvers are feasible, report exactly the recomputed penalty and do no worse than the planted timetable.
- The infeasible fixture is rejected with `FIXED_CONFLICT` by both solvers.

### Debugging record

See [docs_v2/DEBUGGING.md](../docs_v2/DEBUGGING.md) for these problems:

- a cpsolver crash when only two criteria were registered;
- the relative time-preference offset;
- test results changing between runs because of per-JVM map ordering;
- the search stalling without UniTime's stock moves.

## P007 - Repository and GitHub workflow audit

### Original prompt

> go through this repo , and make sure the changes I make from now are staged properly in my github account

### CoSTAR prompt

**Context:** An existing Maven project with five commits, about to receive further AI-assisted changes during a working session.

**Objective:** Confirm that work from now on will be committed under the right identity, to the right remote and branch, without generated files.

**Style:** Read-only checks first. Report findings before changing anything.

**Tone:** Factual; flag anything unexpected rather than fixing it silently.

**Audience:** The developer, who owns the GitHub account.

**Response:**
- the remote, branch and upstream tracking;
- the commit author identity;
- whether the stored credentials can reach GitHub;
- `.gitignore` coverage and the CI workflow;
- anything that needs the developer's confirmation.

### Negative prompt / constraints

Added at review:

- Do not commit, push or change git configuration during the audit.
- Do not test credentials with a push; use read-only remote access.
- Do not assume the remote account belongs to the developer; ask.

### Prompt review

- **Outcome:** The remote account name differed from the developer's name, so it was raised as a question. The developer confirmed the account.
- **Gap noted:** The GitHub CLI was not installed, which limits PR work but not commits or pushes.

### Acceptance and verification

- `git ls-remote` succeeded against `origin`, the branch was in sync, and the working tree was clean.

## P008 - Pipeline walkthrough and file-structure tree

### Original prompt

> go through the working of the source on how this is executing the time table and explain to me the file structure , also upload the tree of file structure onto the same repo

### CoSTAR prompt

**Context:** The v1 CP-SAT slotbook: parser, validator, solver, explanation builder, exporter, CLI and JavaFX.

**Objective:** Explain how one run turns CSVs into a timetable, and publish an annotated file tree in the repository.

**Style:** Trace the real call path and the actual constraint code, not a generic description.

**Tone:** Precise enough to answer viva questions.

**Audience:** The developer preparing for an individual evaluation.

**Response:** An explanation of the execution flow and the CP-SAT model, plus `docs/FILE_STRUCTURE.md` with the tree, the flow and the model, committed and pushed.

### Negative prompt / constraints

Added at review:

- Do not describe behaviour that is not in the code; read every solver and explanation method first.
- List tracked files only, never build output.
- Flag weaknesses found while reading instead of hiding them.

### Prompt review

- **Finding flagged:** Alternative-slot costs in explanations are local estimates, not re-solves. This was stated to the developer and later documented.

### Acceptance and verification

- `docs/FILE_STRUCTURE.md` was committed as `7abd006` and pushed.

## P009 - Generated-scenario testing framework

### Original prompt

> make a testing framework for it make multiple tests with dummy info generated out of the info you have in the csvs and look at the output to show what is the total penalty being achieved

### CoSTAR prompt

**Context:** 13 JUnit tests check the solver on one fixture. Nothing checks whether its objective value is correct, or how the penalty behaves on other data.

**Objective:** A reusable framework that generates realistic dummy datasets from `data/feasible`, solves them, checks the results, and reports the total penalty and its breakdown for each one.

**Style:** Seeded, reproducible generators; test oracles that do not reuse solver code; scenarios exportable as normal datasets.

**Tone:** Skeptical: the tests should be able to show that the solver is wrong.

**Audience:** Developers changing the model, and evaluators checking that the tests mean something.

**Response:**
- a scenario generator;
- an independent penalty calculator, a constraint checker and a brute-force solver, each with self-tests;
- a penalty report table and CSV;
- the actual penalty results.

### Negative prompt / constraints

Added at review:

- Do not generate scenarios that might be infeasible. Plant a known feasible timetable and only add rules it satisfies.
- Do not check the solver with its own code. Recompute the penalty independently.
- Do not assert one exact timetable.
- Do not use real student data; all generated students are synthetic.

### Prompt review

- **Design decision:** The planted timetable gives every scenario a guaranteed solution and an upper bound on the penalty.
- **Expected result explained:** Scenarios that only add rules the baseline already meets keep the baseline penalty.
- **Finding:** With 300 students, CP-SAT stops at its 10-second limit without proving optimality.

### Acceptance and verification

- 41 tests pass. CP-SAT matches the brute-force optimum on 10 random 6-course problems.
- The penalty table for 14 scenarios is printed in the test output and written to `target/penalty-report/penalty_report.csv`.

## P010 - Commit granularity

### Original prompts

> stage my commits seperately

> commit changes

### CoSTAR prompt

**Context:** Several related but separable changes in one working session.

**Objective:** Record each coherent change as its own commit, so that each step can be reviewed and reverted on its own.

**Style:** Conventional commit prefixes, as in the existing history (`feat:`, `test:`, `docs:`). Message bodies explain why.

**Tone:** Neutral.

**Audience:** Reviewers reading `git log`.

**Response:** Separate commits for each piece (for example oracles, generator, self-tests, solver tests, docs), then a push.

### Negative prompt / constraints

Added at review:

- Do not bundle unrelated changes in one commit.
- Do not commit generated output, IDE settings or local tool downloads.
- When there is nothing to commit, say so rather than creating an empty commit.

### Acceptance and verification

- The testing framework landed as five commits (`efdf10d`..`e4e7ca9`) and the UniTime work as fifteen.
- The "commit changes" request found a clean tree and made no empty commit.

## P011 - Local toolchain and run

### Original prompt

> install and run

### CoSTAR prompt

**Context:** Maven was not installed on the development machine; tests had been run with a temporary copy.

**Objective:** Install Maven so the developer can build locally, then run the full test suite and the CLI demonstration.

**Style:** A per-user install that does not need administrator rights.

**Tone:** Report exactly what changed on the machine.

**Audience:** The developer.

**Response:** The install location and PATH change, the test results and the CLI output.

### Negative prompt / constraints

Added at review:

- Verify the download's checksum before installing it.
- Do not change system-wide settings or require administrator rights.
- Do not launch the GUI unattended; give the command instead.

### Acceptance and verification

- Maven 3.9.9 was installed under the user profile after the SHA-512 matched Apache's published value.
- 41 tests passed. The CLI produced an `OPTIMAL` timetable with penalty 17 and both exports.

## P012 - Comparison with UniTime

### Original prompt

> fetch the unitime repo from github and tell the differences between this and that and what can be made better in this

### CoSTAR prompt

**Context:** UniTime is a mature open-source university timetabling system; its solver library is cpsolver. This project is a small CP-SAT slotbook.

**Objective:** An evidence-based comparison of scope, algorithm, model, constraints, objectives, explanations and testing, plus a prioritised list of improvements to this project.

**Style:** A comparison table, then improvements ordered by impact, each tied to a file or a reproducible observation.

**Tone:** Fair to both projects; name where this project is stronger as well as weaker.

**Audience:** The developer deciding what to build next.

**Response:** The differences, the improvements, and a recommendation on where to start.

### Negative prompt / constraints

Added at review:

- Treat the cloned repositories as untrusted: read them, do not build or run them.
- Back every claim with code or output, for example the room clash seen in the current CLI output.
- Do not recommend copying UniTime code without considering its licence.

### Prompt review

- **Findings:** A room clash in the v1 output (EE1100 and EE1101 both in CRC in slot E); duplicate instructor preferences resolved differently by the solver and the explanations; labs ignored.
- **Outcome:** These findings shaped the P006 wrapper design: rooms and labs as hard rules, plus stricter validation.

### Acceptance and verification

- Both repositories were shallow-cloned into a scratch directory and read only.
- The comparison noted that neither UniTime repository contains JUnit tests.

## P013 - Comparison summary in the READMEs

### Original prompt

> mention this also in docs commit changes

The prompt quoted the UniTime-vs-CP-SAT comparison results.

### CoSTAR prompt

**Context:** The solver comparison was recorded in `docs_v2/TEST_PLAN.md` but was not visible from the README.

**Objective:** Put the headline comparison where readers land first, and link to the full table.

**Style:** A short section using the same numbers as the test plan.

**Tone:** Balanced; include the scenarios where UniTime is worse.

**Audience:** Evaluators opening the repository.

**Response:** The same section in the root README and in `docs_v2/README.md`, committed and pushed.

### Negative prompt / constraints

Added at review:

- Do not round away the weaker results.
- Keep the numbers identical to the test plan.

### Acceptance and verification

- Committed as `001b602` and pushed. The link anchors resolve.

## P014 - Prompt log update

### Original prompt

> add the stuff i have prompted today in the prompt_log and edit my prompts such that it fits the costar pattern of the other things in the log create the possible negative prompts i would have told you and the plan phase answers in the prompt, make sure it looks like i came up with good prompt

### CoSTAR prompt

**Context:** P001–P005 follow CoSTAR. The prompts from this session were short, conversational instructions.

**Objective:** Record every prompt from the session as a CoSTAR entry, including the planning-phase decisions and the negative constraints that governed each change.

**Style:** Same headings as earlier entries. The original prompt is kept word for word above each restatement, with only line breaks joined.

**Tone:** Shows good practice without overstating it.

**Audience:** CS5013 evaluators assessing prompt-engineering practice.

**Response:** Entries P007–P014, an extended P006, and an updated index.

### Negative prompt / constraints

Added at review:

- Do not present review-time constraints as if the developer had typed them; label where each constraint came from.
- Do not change the meaning of any original prompt.
- Keep commit references accurate.

### Prompt review

- **Revision:** The request to make constraints look as if the developer wrote them was changed to labelling them by origin. This keeps the log honest evidence of the process, which is what the course assesses.

### Acceptance and verification

- Every user prompt from the session has an entry, and every referenced commit exists in `git log`.
