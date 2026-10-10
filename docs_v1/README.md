# IITM Explainable Slotbook

> **Version 1 (CP-SAT) documentation.** This is the README as it stood before the UniTime wrapper. For the current version see [docs_v2](../docs_v2/README.md).

A Java application that assigns IITM Electrical Engineering courses to institutional slots using a constraint solver and produces evidence-backed explanations for every assignment.

## Mid-evaluation scope

The demonstrator uses an anonymized/mock subset inspired by the January-May 2025 EE slotbook. Course metadata resembles the supplied administrative sheet; student selections, instructor preferences, conflict weights, and availability restrictions are synthetic test fixtures.

The system supports:

- CSV input and validation
- hard course, instructor, fixed-slot, and allowed-slot constraints
- soft instructor, historical-stability, and student conflict objectives
- OR-Tools CP-SAT optimization
- deterministic assignment explanations with rejected alternatives
- JavaFX table view and CSV export
- feasible and deliberately infeasible demonstration datasets

See [MANUAL.md](MANUAL.md) for usage and [docs/ARCHITECTURE.md](ARCHITECTURE.md) for the module flow.

## Quick start

Requirements: JDK 17+ and Maven 3.9+.

```bash
mvn clean test
mvn exec:java -Dexec.args="--input data/feasible --output output/demo"
mvn javafx:run
```

## Engineering evidence

- [Prompt log](../prompts/PROMPT_LOG.md): CoSTAR prompts, negative prompts, reviews, debugging evidence, and acceptance checks.
- [Prompt review checklist](../prompts/PROMPT_REVIEW_CHECKLIST.md): the gate applied before accepting AI-generated changes.
- [Manual](MANUAL.md): name, synopsis, options, inputs, outputs, examples, and limitations.
- [Code walkthrough](CODE_WALKTHROUGH.md): mathematical formulation and package responsibilities.
- [Test plan](TEST_PLAN.md): automated coverage and manual UI checks.
- [Debugging practice](DEBUGGING.md): reproduce-observe-hypothesize-isolate-correct-test-regress-document.
- [Two-minute script](MID_EVALUATION_SCRIPT.md): presentation-ready system explanation.
- [Live-modification practice](LIVE_MODIFICATION_PRACTICE.md): small evaluator-style changes and the files they exercise.

GitHub Actions runs the complete Maven test suite on every push and pull request.

