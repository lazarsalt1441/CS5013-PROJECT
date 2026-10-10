# Test Plan

## Automated tests

| Test class | Behaviour verified |
|---|---|
| `CsvSupportTest` | Quoted commas and escaped quotes parse correctly |
| `DatasetLoaderTest` | All five fixture types load with expected counts |
| `DatasetLoaderTest` | Student selections produce positive co-demand weights |
| `InputValidatorTest` | Feasible fixture contains no validation errors |
| `InputValidatorTest` | Contradictory fixed courses produce `FIXED_CONFLICT` |
| `OrToolsTimetableSolverTest` | Every course receives an assignment |
| `OrToolsTimetableSolverTest` | Every assignment lies in the allowed domain and respects fixed slots |
| `OrToolsTimetableSolverTest` | Every hard-conflict pair receives different slots |
| `OrToolsTimetableSolverTest` | Every course receives a complete explanation |
| `OrToolsTimetableSolverTest` | Known invalid fixture is rejected before solving |
| `SlotbookExporterTest` | Both CSV exports exist and contain assignment/explanation evidence |

## Manual smoke tests

1. Run `mvn javafx:run`.
2. Load `data/feasible` and click **Generate slotbook**.
3. Confirm 18 rows appear and status is `OPTIMAL` or `FEASIBLE`.
4. Select `EE2002`; confirm its explanation identifies fixed slot B.
5. Select a non-fixed course; confirm all six alternative slots are classified.
6. Export to a new directory and open both CSV files.
7. Load `data/infeasible-fixed-conflict`; confirm `FIXED_CONFLICT` appears and no timetable is displayed.

## Regression policy

- Run the full suite after every behavioural change.
- Do not assert one exact timetable unless uniqueness is intentionally guaranteed.
- Add a negative test for every validation or parsing bug.
- Keep generated outputs out of Git; retain only deterministic input fixtures and test expectations.
