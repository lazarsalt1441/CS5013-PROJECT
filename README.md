# IITM Explainable Slotbook (v2: UniTime wrapper)

A Java application that assigns IITM Electrical Engineering courses to institutional slots and produces evidence-backed explanations for every assignment. Version 2 hands the optimisation to [UniTime](https://www.unitime.org)'s course timetabling engine, [cpsolver](https://github.com/UniTime/cpsolver), and keeps the v1 CP-SAT model as an exact reference solver.

Documentation: [docs_v2](docs_v2/README.md) describes this version; [docs_v1](docs_v1/README.md) keeps the original CP-SAT version.

## What changed from v1

| | v1 | v2 |
|---|---|---|
| Default solver | OR-Tools CP-SAT | UniTime cpsolver (`--solver cpsat` still available) |
| Time model | Slot labels, never overlapping | Real IITM meeting times from the official grid; overlaps computed to 5 minutes |
| Labs (`additional_slot`) | Displayed only | Fixed lab meetings that block instructors, conflicts and students |
| Rooms | Displayed only | Hard rule: one course per room at a time |
| Validation | 9 checks | 18 checks, including duplicate or negative preferences and lab clashes |
| Tests | 41 | 52, comparing both solvers on every scenario |

## Scope

The demonstrator uses an anonymized/mock subset inspired by the January–May 2025 EE slotbook. Course metadata resembles the supplied administrative sheet; student selections, instructor preferences, conflict weights and availability restrictions are synthetic test fixtures. Slot times follow the official grid at <https://www.iitm.ac.in/timetable>.

The system supports:

- CSV input and validation
- hard course, instructor, room, lab, fixed-slot and allowed-slot constraints
- soft instructor, historical-stability and student-conflict objectives
- UniTime search (construction with conflict-based statistics, hill climbing, great deluge) or exact CP-SAT
- deterministic assignment explanations with rejected alternatives
- JavaFX table view with solver selection, and CSV export
- feasible and deliberately infeasible demonstration datasets

See [MANUAL.md](MANUAL.md) for usage and [ARCHITECTURE.md](docs_v2/ARCHITECTURE.md) for the module flow.

## Quick start

Requirements: JDK 17+ and Maven 3.9+.

```bash
mvn clean test
mvn exec:java -Dexec.args="--input data/feasible --output output/demo"
mvn exec:java -Dexec.args="--input data/feasible --output output/demo --solver cpsat"
mvn javafx:run
```

## Engineering evidence

- [Prompt log](prompts/PROMPT_LOG.md): CoSTAR prompts, negative prompts, reviews, debugging evidence and acceptance checks.
- [Prompt review checklist](prompts/PROMPT_REVIEW_CHECKLIST.md): the gate applied before accepting AI-generated changes.
- [Manual](MANUAL.md): name, synopsis, options, inputs, outputs, examples and limitations.
- [About](docs_v2/ABOUT.md): the problem, what "explainable" means and data provenance.
- [Code walkthrough](docs_v2/CODE_WALKTHROUGH.md): the UniTime mapping, the time encoding and the objective equivalence.
- [Test plan](docs_v2/TEST_PLAN.md): automated coverage, solver comparison and manual UI checks.
- [Verification record](docs_v2/VERIFICATION.md): the results recorded for this version.
- [Debugging practice](docs_v2/DEBUGGING.md): reproduce-observe-hypothesize-isolate-correct-test-regress-document.
- [Two-minute script](docs_v2/MID_EVALUATION_SCRIPT.md): presentation-ready system explanation.
- [Live-modification practice](docs_v2/LIVE_MODIFICATION_PRACTICE.md): small evaluator-style changes and the files they exercise.
- [Principles and practices](docs_v2/PRINCIPLES_AND_PRACTICES.md): prompt engineering, debugging, version control and testing.
- [File structure](docs_v2/FILE_STRUCTURE.md): annotated repository tree.

GitHub Actions runs the complete Maven test suite on every push and pull request.

## Licensing

cpsolver is distributed under the GNU Lesser General Public License v3. This project uses it unmodified as a Maven library dependency.
