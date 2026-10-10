# IITM Explainable Slotbook

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

See [MANUAL.md](MANUAL.md) for usage and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the module flow.

