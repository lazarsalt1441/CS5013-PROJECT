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
| P010 | Install the toolchain and run the tests and CLI locally | Accepted | no code change |
| P011 | Compare the project with UniTime and list improvements | Accepted | analysis; led to P006 |
| P012 | Build a Vercel preference portal with a free Google Sheets backend | Accepted | current portal commit |
| P013 | Build and verify a deliberately explainable showcase dataset | Accepted | `487a534`..`8269d7f` |
| P014 | Package the current system for the CS5013 mid-evaluation | Accepted | current documentation commit |
| P015 | v3: replace Google Sheets with Postgres (Neon), add grade-card prerequisite warnings, docs_v3 and man page | Accepted (PR #1) | `ebb9c28`..current |
| P016 | Document the tool's functionality and project progress | Accepted | this commit |
| P017 | Add stress tests for the portal and the prerequisite checker | Accepted (PR #4) | `d2319e6`..current |
| P019 | Apply lecture practices not yet used: coverage, mutation, property-based testing, static analysis, dependency audit, licence notices | Accepted | see P019 |
| P020 | Make the admin page's "Generate slotbook" button work: a solver web service in the Docker image | In progress | see P020 |

P006–P011 were all written on 10 October 2026. In order of use they ran P007, P008, P009, P010, P011, P006. P006 keeps its number because commit `3c9d6cb` refers to it. P012 was added on 11 October 2026.

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

## P010 - Local toolchain and run

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

## P011 - Comparison with UniTime

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

## P012 - Preference collection and publication portal

### Original prompts

> hey ive made it into a wrapper around the other thing, can you make ur gpt make a vercel.ai host a site for ppl to put input like to collect students_pref.csv and prof_pref.csv sort of data, that we can fit into our model in backend to give out slotbooks can yu please do this

> Demo without authentication. can we use google sheets, or whatver is free. everything?

### CoSTAR prompt

**Context:** The v2 project wraps UniTime, retains CP-SAT as a reference, and consumes `student_preferences.csv` and `instructor_preferences.csv`. The project needs a low-cost demonstration workflow for collecting these preferences before slotbook generation.

**Objective:** Build a Vercel-ready portal with separate student, professor, administrator, and public-result experiences. Store submissions in a free Google Sheet, export the exact Java CSV contracts, support manual solver outputs, and expose a configurable HTTP hook for a deployed Java solver.

**Style:** Dependency-free HTML, CSS, browser JavaScript, and small Node.js Vercel functions. Use explicit validation, accessible controls, responsive layouts, and clearly separated public and administrative actions.

**Tone:** Institution-facing but honest that this is a demonstration without IITM authentication.

**Audience:** Students and faculty submitting preferences, administrators generating a slotbook, and evaluators tracing the data into the solver.

**Response:** Add the portal source, Google Apps Script bridge, Vercel configuration, environment template, deployment manual, CSV export, solver adapter, results publication, automated API contract tests, and CI checks.

### Negative prompt / constraints

Stated or agreed with the developer:

- Do not require paid storage; use Google Sheets / Apps Script for the first deployment.
- Do not require student or professor authentication in the demonstration.
- Include the student, professor, administrator, solver, and public-results workflows.

Added at review:

- Do not write production submissions to Vercel's ephemeral filesystem.
- Do not embed administrator, Google, GitHub, or solver secrets in browser code or Git.
- Do not rename or approximate the Java input schemas.
- Do not describe a soft instructor penalty as hard unavailability.
- Do not claim the Java solver runs inside Vercel when only an external HTTP hook is configured.
- Do not invite real institutional data before identity, consent, retention, and abuse controls exist.

### Prompt review

- **Storage:** Apps Script serializes writes into `Students`, `Instructors`, `Slotbook`, and `Explanations` tabs in one free Google Sheet.
- **Idempotency:** A repeated student ID or instructor name replaces that identity's earlier rows instead of silently duplicating a profile.
- **Schema fit:** Admin exports are exactly `student_id,course_id,rank` and `instructor,slot,penalty,reason`.
- **Deployment boundary:** Vercel collects and exports data. A separately deployed JVM endpoint may run UniTime/CP-SAT; the manual CSV workflow remains complete without it.
- **Security:** Student and professor forms are deliberately public; admin actions require two server-side secrets, one at Vercel and one at Apps Script.

### Acceptance and verification

- Catalog generation reads the repository fixture and produces 18 courses and 17 institutional slots.
- Seven Node contract tests verify CSV quoting, public submission routing, Apps Script redirect handling, input-type rejection, admin authorization, exact exports, and the unconfigured-solver fallback.
- The static validator checks required assets, JavaScript syntax, JSON, HTML asset references, unique catalog IDs, and both live Java CSV headers.
- GitHub Actions runs the portal tests and validator independently of the Maven job.
- Live deployment testing caught Google's one-time `ContentService` redirect returning an HTML 404 when a POST redirect was followed automatically. The Vercel bridge now retrieves the validated `script.googleusercontent.com` response URL explicitly with a GET, with a regression test covering the behavior.
- Final browser interaction and Google/Vercel environment checks are completed after the external deployments are configured.

## P013 - Mid-evaluation showcase dataset

### Original prompt

> can you make some nice test case, take it as demo data for us to show a generated slotbook

### CoSTAR prompt

**Context:** The deployed portal collects solver-compatible preferences, while the Java wrapper needs a concise scenario that visibly demonstrates fixed courses, laboratories, shared rooms, protected pairs, faculty preferences, historical stability and student co-demand.

**Objective:** Create a synthetic mid-evaluation dataset, generate it through both UniTime and CP-SAT, and publish an explainable slotbook that can be demonstrated in two minutes.

**Style:** Keep the case small and narratively clear. Use recognisable preference clusters and enough competing demand to force a genuine optimisation trade-off. Include a formatted workbook for inspection and the five canonical CSV inputs for the solver.

**Tone:** Evaluator-facing, factual and easy to defend from the code and output.

**Audience:** CS5013 evaluators and team members demonstrating the portal-to-solver workflow.

**Response:** Add `data/demo-showcase`, an evaluator workbook, documented commands, CI generation with both solvers, downloadable output artifacts, and a published slotbook with deterministic explanations.

### Negative prompt / constraints

Added at review:

- Do not use real roll numbers, names or submitted student preferences.
- Do not hand-author the final slotbook; generate it with the repository's solver workflow.
- Do not claim every preference is satisfied; surface the lowest-cost unavoidable conflict.
- Do not make all constraints soft. Fixed slots, labs, protected pairs and room collisions remain hard rules.
- Do not make the case so large that its trade-offs cannot be explained during a two-minute evaluation.
- Do not publish a result until both input validation and CI generation succeed.

### Prompt review

- **Scenario shape:** Eleven courses include two fixed core courses, one fixed laboratory and eight electives competing for seven lecture slots.
- **Demand model:** Thirty-six anonymous students produce 144 ranked rows. Six pair-coverage profiles supplement the four main demand clusters so every pair among the eight electives has positive co-demand.
- **Traceability:** Six protected pairs and twenty instructor preference rows create alternatives that explanations can classify as impossible or more costly.
- **Presentation:** The workbook puts the scenario metrics and a five-step evaluator walkthrough before the underlying source tabs.

### Acceptance and verification

- The workbook contains the same five solver inputs as the CSV fixture and contains no formula errors.
- GitHub Actions runs the normal tests, then generates UniTime and CP-SAT outputs for the showcase and uploads both as an artifact.
- The first draft produced zero student conflicts, so it was rejected as a weak demonstration. Six synthetic pair-coverage profiles were added, and the validator now proves that all 28 elective pairs have positive co-demand.
- CI run 13 passed every portal and Java step. UniTime returned objective 9 in 10.108 seconds; CP-SAT proved objective 9 optimal in 0.199 seconds.
- Both outputs place EE3110 and EE5175 together in slot B, leaving the minimum-weight unavoidable elective conflict. Their explanations each report student-conflict contribution 3.
- The four solver-generated CSVs are checked in under `data/demo-showcase/generated`; they are not hand-authored.
- The UniTime result is published to the live portal after the final upload verification.

## P014 - Mid-evaluation submission and viva pack

### Original prompt

> Dear Students, Here is the format, expectations, and deliverables for the upcoming Mid-Evaluation... Please submit a pdf outlining how you are applying the engineering principles taught in class across your project lifecycle. The document must be 3 pages max... System Architecture / Flowchart... Brief Presentation (2 Minutes)... Live Code Modification...

### CoSTAR prompt

**Context:** The repository now contains a UniTime wrapper, CP-SAT reference, preference portal, prompt register, automated tests, and a CI-verified showcase that is published on the live results page.

**Objective:** Produce a submission-ready, maximum-three-page practices PDF and a compact viva pack that reflects the current portal-to-publication system rather than the older solver-only scope.

**Style:** Evidence-led academic engineering document with readable tables, a clear system diagram, timed speaking notes, and small code-change drills.

**Tone:** Precise, non-promotional, and honest about synthetic data, heuristic limits, and the demonstration authentication boundary.

**Audience:** CS5013 TAs evaluating prompt practice, debugging, Git usage, testing, architecture understanding, and individual code ownership.

**Response:** Add an editable DOCX, exactly three-page PDF, SVG and PNG architecture soft copy, two-minute script, demonstration order, viva answers, and live-modification exercises.

### Negative prompt / constraints

- Do not exceed three PDF pages.
- Do not reuse outdated v1 test counts or omit the preference portal.
- Do not invent test evidence or claim that local Maven ran when only CI evidence is available.
- Do not describe an LLM as the source of solver decisions or authoritative explanations.
- Do not include secrets, real student data, or production-authentication claims.
- Do not create presentation material that the team cannot trace to concrete files.

### Prompt review and acceptance

- Claims were cross-checked against the CI workflow, test sources, prompt log, demo validator, retained generated outputs, and the live public result.
- The document distinguishes executed local Node checks from the last verified Java CI result.
- The architecture includes collection, persistence, validation, both solvers, deterministic explanation, and publication.
- The DOCX and PDF are rendered page by page before acceptance; the PDF must report exactly three pages.

## P015 - v3: Postgres backend, grade-card prerequisite warnings, man page

Written on 2 November 2026. The planning questions and answers are kept because they changed the scope.

### Original prompts

> hi, can you look at the whole project, and now change the backend from gogolre sheets to a proper database, take one based on what you would think is good let me know if you dont have access to th repo, also add featurr to be able to enforce prereqs, and also write proper unit tests and follow good software practices, log your prompts and also use good ai practices Params 1) docs done correctly - has all the tests Negative prompts Etc 2) code commit at regular intervals 3) Are the prompts logged 4) Use the learning in the course to buildWriting Man page About the tool Options of the tool ask me for questions and then start working

Planning answers (multiple choice and free text):

> Database: Postgres on Neon. Prerequisite completions: "lets make it such that the student uploads their grade card, with extention to get this info from admin(make sure we can extend to this, but not right now)". Enforce at: portal and Java.

> ok do this, take sample, lets just say non u,w,i for now, lets say the prof will give prereq list, make it a warning and not a enforcement, make it a new version ig

### CoSTAR prompt

**Context:** The v2 repository has a Java UniTime/CP-SAT slotbook engine and a dependency-free Vercel portal whose storage is a Google Apps Script bridge over a Google Sheet (P012). The portal exports `student_preferences.csv` and `instructor_preferences.csv` for the Java CLI. There is no notion of prerequisites. A real IITM grade-card PDF was supplied as a format sample.

**Objective:** Produce v3. (1) Replace the Sheets bridge with a relational Postgres database hosted on Neon, behind a storage interface, with schema migrations and tests that run without a network. (2) Let professors declare prerequisites for their course, let students upload their grade card, and warn (never block) when a ranked course has a prerequisite the grade card does not show as completed. Grades U, W and I do not count as completed. (3) Mirror the same warning in the Java CLI from optional CSV inputs. (4) Ship docs_v3, a `slotbook(1)` man page and an updated manual that lists every test.

**Style:** Small, single-purpose modules; dependency injection for storage and completion sources; SQL with constraints instead of application-only checks; Node built-in test runner and JUnit 5 to match the existing suites.

**Tone:** Engineering documentation that states limits plainly (self-uploaded grade cards are unverified).

**Audience:** CS5013 evaluators checking prompt practice, tests, commits and documentation; future maintainers adding admin-provided records.

**Response:** Commits at each working step, each referencing P015: prompt log, storage interface and Postgres store, migrations, grade-card parser, prerequisite checker, API and UI changes, Java loader/checker/CLI changes, tests, docs_v3, man page, CI.

### Negative prompt / constraints

Stated by the developer:

- Do not enforce prerequisites: a missing prerequisite is a warning and the submission is still saved.
- Do not count U, W or I grades as completed.
- Do not build the admin-provided completion source yet, but do not make adding it require a schema or form change.
- Do not overwrite the v2 documentation; this is a new version.

Agreed during planning:

- Do not keep Google Sheets as a runtime dependency.
- Do not use a database without a free tier reachable from Vercel serverless functions.

Added at review:

- Do not commit the supplied grade card or any real student name or roll number; tests use a synthetic card in the same layout.
- Do not store the uploaded PDF; store only the parsed course, grade and term rows.
- Do not build SQL by string concatenation; every query is parameterised.
- Do not let tests depend on a live database or the network.
- Do not change the existing CSV schemas consumed by the Java loader; new inputs are new, optional files.
- Do not let an unparseable grade card crash a submission; report it as a warning.
- Do not claim the grade card is verified; the admin-records source is the future verified path.
- Do not invent test counts in the docs; copy them from the test runs.

### Prompt review

- **Scope correction during planning.** "Enforce prereqs" became advisory warnings. "A proper database" became Postgres on Neon, chosen for its free tier, Vercel integration, constraints and transactions. Completed courses come from student-uploaded grade cards, with admin records as a built-in extension point rather than a feature.
- **Storage.** The Apps Script bridge was replaced by a store module that is the only SQL in the portal. Every query is parameterised. Validation rules were ported unchanged and repeated as database constraints.
- **Privacy.** The supplied grade card was used once, locally, to validate the parser on the real layout (43 of 43 rows). It was not committed and its contents are not quoted anywhere. Fixtures are generated and marked `SYNTHETIC`, and `npm run check` enforces the marker.
- **Solver isolation.** Prerequisite data is kept outside `TimetableProblem`. A test with a recording solver proves the solver input is identical with and without it.
- **Parity.** One golden file (`data/prerequisite-warnings/expected_warnings.csv`) is checked by the JUnit suite, the Node suite and a CI diff of the real CLI output.
- **Rejected or revised AI output:**
  - a fixture guard that grepped raw PDF bytes; it passed for the wrong reason, because the streams are compressed;
  - a man-page draft that gave the wrong exit status for invalid input;
  - a claim that the JavaFX app "compiles without edits", reworded because it could not be compiled here.
- **Faults found by the new tests** (details in `docs_v3/DEBUGGING.md`):
  - v2 never applied the results cache header;
  - pg-mem silently ignores `ROLLBACK`, so rollback tests moved to real Postgres;
  - the portal counted U/W rows as completed on non-grade-card paths;
  - the Java loader defaulted a missing `source` column to `null`.

### Acceptance and verification

Commits, each leaving the suites green: `ebb9c28` (this log entry), `7591cca` (Postgres), `6bfd4a1` (grade card and portal warnings), `f528505` (Java warnings and parity fix), `9ee919b` (docs_v3 and man page), and this review.

| Check | Where | Result |
|---|---|---|
| Portal unit and contract tests | local (Node 22); CI after the Node 22 fix | 56/56 |
| Portal integration tests | local PostgreSQL 16.15 | 4/4; all 4 skip without `TEST_DATABASE_URL` |
| `npm run check` | local | pass; rejected a real grade card when one was copied in |
| Browser end to end | local, headless Chrome | professor, student and admin flows as documented; no console errors |
| New Java tests (19) | local, `javac --release 17` with JUnit stand-ins | 19/19 |
| Full Maven suite (71) | CI run 41429007442 | **71/71**; CLI demo printed 22 warnings and matched the golden file |
| Man page | local groff | lint clean; committed rendering current |

The full record is in `docs_v3/VERIFICATION.md`. The first CI run on PR #1 passed the Java job (71/71) but failed 5 portal tests on Node 20. The cause was that `unpdf` requires Node 22, and a catch-all hid it behind a "bad PDF" message. Both were fixed in a follow-up commit; see `docs_v3/DEBUGGING.md` §8.

## P016 - Functionality and progress document

Written on 2 November 2026, after PR #1 (v3) was merged.

### Original prompt

> Apparently we need to commit a doc about the functionalities of the tool along with the progress in the project

### CoSTAR prompt

**Context:** v3 is merged on `main` (`8f2c000`). The repository has per-version docs (`docs_v1` to `docs_v3`), a prompt log P001–P016 and 49 commits from 10 October to 2 November 2026.

**Objective:** One committed document that states what the tool does today, per user role and per component, and how the project reached this point: milestones, versions, test growth, open limitations and next steps.

**Style:** A reference document with tables, linked to the detailed docs rather than repeating them.

**Tone:** Factual. Status labels distinguish done, partial and planned work.

**Audience:** CS5013 evaluators and teammates who need the current state in one place.

**Response:** `FUNCTIONALITY_AND_PROGRESS.md` at the repository root, linked from the README and the docs_v3 index, committed with this entry.

### Negative prompt / constraints

Added at review:

- Do not claim a feature works unless a test or a recorded run shows it; mark partial or untested work as such.
- Take every date, commit and test count from Git, CI or the existing test plans; do not estimate.
- Do not list deployment as done; the Neon database and Vercel settings are the team's to apply.
- Do not duplicate the manual or test plan; link to them.

### Acceptance

- Every date and hash was checked against `git log origin/main`; test counts come from the v1, v2 and v3 test plans and CI run 41429470032.
- All relative links resolve.

## P017 - Stress tests

Written on 4 November 2026.

### Original prompt

> can you put some stress test unit test please

### CoSTAR prompt

**Context:** v3 is on `main` with 71 Java tests, 56 portal unit/contract tests and 4 Postgres integration tests. None of them exercise load, volume or hostile input.

**Objective:** Add stress tests that try to break the system under concurrency, volume and randomized input, and that fail loudly on wrong results rather than only on crashes.

**Style:** Seeded and reproducible; every stress test checks results against an invariant or an independent oracle, not only "no exception".

**Tone:** Engineering; report any fault found.

**Audience:** Maintainers and CS5013 evaluators.

**Response:** A portal stress suite (`npm run test:stress`) with a real-Postgres part; a Java stress test class; CI wiring; test-plan and prompt-log updates.

### Negative prompt / constraints

Added at review:

- Do not make the default `npm test` slow or network-dependent; stress tests get their own script.
- Do not use unseeded randomness; a failure must print the seed and be replayable.
- Do not assert tight wall-clock limits that flake on shared CI runners; use generous bounds or scaling ratios.
- Do not exceed the database's connection limit by accident; size pools explicitly.
- Do not weaken production code to make a stress test pass.

### Prompt review

- Every stress test asserts an invariant or compares against an oracle written independently of the code under test (naive restatements of the documented rule in JavaScript and in Java).
- Tests that found faults were re-run against the old code to confirm they fail there.
- Three faults in product code were found and fixed; three test-design mistakes were corrected (`docs_v3/DEBUGGING.md` §10–12).

### Acceptance

| Check | Where | Result |
|---|---|---|
| Portal unit and contract | local | 57/57 |
| Portal integration | local Postgres 16 | 4/4 |
| Portal stress | local Postgres 16, seeds 20261104, 1, 2, 3, 7, 8 | 14/14; about 18 s |
| Java new and stress tests | local `javac --release 17`, JUnit stand-ins | 23/23; the stress class takes about 4 s |
| Full Maven suite and CI stress step | CI run 41704760988 on PR #4 | Maven 75/75; portal 57/57, integration 4/4, stress 14/14 (scaling 1.53x and 1.30x against a limit of 5) |

## P019 - Lecture practices not yet applied

Written on 6 November 2026.

### Original prompt

> these are the lectures, put the things we followed, if we havent, try that out

(The course slides for Modules 1-7 and 9 were supplied.)

### CoSTAR prompt

**Context:** The final report must cite course concepts with file paths and commits. A review of the slides against the repository found that most practices are already in use (CoSTAR and negative prompting; git, Conventional Commits and CI; structured debugging; fuzzing; docs as code; secrets in environment variables). Several practices the slides name explicitly are not yet in use: JaCoCo coverage, PIT mutation testing and jqwik property-based tests (Module 5); SpotBugs with the "static analyser first, then LLM, smallest patch" loop (Module 4); dependency auditing (Module 9); licence and provenance notices for redistributed code (Module 7).

**Objective:** Apply each of those practices to this project, act on what they find, and record the evidence. Do not just switch the tools on.

**Style:** Use the tools the slides name. Each tool runs in CI. Every finding is triaged as fixed or justified.

**Tone:** Report the numbers as measured, including unflattering ones.

**Audience:** CS5013 evaluators.

**Response:** pom.xml plugins and a `quality` CI job; jqwik properties; tests that kill surviving mutants; SpotBugs fixes; `npm audit` in CI; `THIRD_PARTY_NOTICES.md` shipped in the Docker image; docs updated.

### Negative prompt / constraints

Added at review:

- Do not add an LLM to the product to tick off Module 6; the design keeps models out of the decision path (NG1).
- Do not change behaviour while fixing static-analysis findings unless the finding is a real bug; prefer the smallest patch (Module 4).
- Do not chase a coverage percentage; use mutation results to judge test strength (Module 5).
- Do not suppress a finding without a written reason.
- Do not guess licences; read them from package metadata.
- Do not slow the default `mvn test`; mutation testing runs in its own CI job.

### Acceptance

Accepted on 6 November 2026 after five CI rounds on PR #7; the last is all green.

- jqwik found a real fault on its first run (touching off-grid meetings counted as a clash); fixed by rejecting off-grid times (DEBUGGING §13).
- SpotBugs: 10 findings, of which 3 were a real locale bug (Turkish `I`); 0 remain, and new findings fail CI (§14).
- PIT: 91.0% to 98.7% (151 of 153); the 2 survivors are equivalent and documented (§15); the build fails below 97%.
- JaCoCo: 79.3% lines, 75.7% branches. Portal coverage: 96.4% lines, 90.6% branches. `npm audit`: 0 vulnerabilities.
- Licences: 16 runtime dependencies, read from the generated report; LGPL cpsolver ships as a separate replaceable jar; licence texts are in the Docker image under `/app/licenses`.
- Java tests: 75 to 93.

Rejected along the way: two CI failures were my own compile errors (a stray brace and a missing import), and one property I wrote was wrong for students with no grade card. All three were caught by CI or by the property run, not by reading. Property tests are now type-checked locally against stub APIs before pushing.

## P020 - Solver web service for "Generate slotbook"

Written on 7 November 2026.

### Original prompt

> Make the button work. [...] add a small web server around the solver to the Docker image (about 150 lines of Java, no new libraries), with tests; deploy that image on a free host that runs Docker (e.g. Render) [...]; put the host's address in Vercel as `SOLVER_API_URL`. [...] lets do this

### CoSTAR prompt

**Context:** The portal's admin page has a "Generate slotbook" button that POSTs the current submissions to `SOLVER_API_URL` and publishes the reply (`portal/api/solver.js`). No such service exists, so the admin downloads CSVs, runs the CLI and uploads the outputs by hand. The portal's course catalog is generated from `data/feasible`. The final demo and submission are the next day.

**Objective:** A small HTTP service in the existing Docker image that accepts the portal's payload, merges the portal submissions onto `data/feasible` (a person's portal rows replace their sample rows, as `scripts/live-demo.sh` does), solves with the existing service layer and returns the slotbook and explanations in the shape `validatePublication` accepts.

**Style:** JDK only (`com.sun.net.httpserver`), no new dependencies. Same solver, validator and explanation code as the CLI, so the website and the CLI cannot disagree.

**Tone:** Conservative; this is added the night before submission.

**Audience:** The admin pressing the button during the demo; evaluators reading the code.

**Response:** `server` package, tests, Docker entrypoint for server mode, a CI smoke test of the container, Vercel function time limit, docs and deployment steps.

### Negative prompt / constraints

Added at review:

- Do not change the CLI, solver, validator or the portal's request format; the CLI default `docker run` must behave exactly as before.
- Do not add a JSON or web library (Module 9: every new dependency is supply-chain risk).
- Do not run the service without a key in production: when `SOLVER_API_KEY` is set, every solve needs `Authorization: Bearer <key>`, compared in constant time.
- Do not trust the payload: limit the body size, reject CSV headers that differ from the dataset's, and run one solve at a time.
- Do not leave temporary datasets behind.
- Do not claim it works on the host until a real request has gone through it.

### Acceptance

(Filled in after CI.)

