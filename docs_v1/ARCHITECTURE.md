# Architecture

```mermaid
flowchart LR
    A[CSV datasets] --> B[CSV parser]
    B --> C[Input validator]
    C -->|valid| D[OR-Tools CP-SAT solver]
    C -->|invalid| E[Validation report]
    D -->|feasible| F[Solution and evidence]
    D -->|infeasible| G[Diagnostic report]
    F --> H[Explanation builder]
    H --> I[JavaFX view]
    H --> J[CSV export]
```

## Dependency rule

`model` contains immutable domain records. `io` translates CSV rows into those records. `validation` rejects malformed or contradictory input. `solver` owns all optimization logic. `explain` converts solver evidence into deterministic user-facing reasons. `ui` and `cli` depend on the application service and never construct solver variables directly.

This separation makes live modifications local and testable: changing a constraint should normally affect the solver and its tests, while changing presentation should affect only UI/export code.

