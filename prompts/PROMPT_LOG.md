# Prompt Log and Review Register

Every substantial AI-assisted change is recorded before implementation. Each entry contains the prompt, its CoSTAR structure, negative constraints, review notes, accepted changes, and verification evidence. Prompt entries are committed with the code they influenced.

| ID | Change | Status | Commit |
|---|---|---|---|
| P001 | Establish project architecture and engineering workflow | Accepted | pending |
| P002 | Create auditable mock slotbook and student-preference fixtures | Accepted | pending |
| P003 | Implement parsing, validation, CP-SAT optimization, and explanations | Accepted | pending |
| P004 | Test feasible, infeasible, parsing, explanation, and export behavior | Accepted | pending |

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




