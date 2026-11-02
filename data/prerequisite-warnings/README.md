# Prerequisite-warnings dataset (v3)

The `data/feasible` problem plus the two optional prerequisite inputs. The five
solver files are byte-for-byte copies, so the slotbook is the same as for
`data/feasible`; only the advisory warnings differ. All data is synthetic.

| File | Meaning |
|---|---|
| `prerequisites.csv` | 4 rules: EE5141 needs EE3110 and EE1101; EE5112 and EE5143 need EE3110 |
| `completed_courses.csv` | Grade-card rows for three students |

Cases it exercises:

| Student | Record | Ranked (constrained) | Expected |
|---|---|---|---|
| S001 | EE1101 A, EE3110 B | EE5141, EE5112 | no warning |
| S002 | EE1101 S, EE3110 **U** | EE5141 | `MISSING_PREREQUISITE` EE3110 (U does not count) |
| S005 | EE1101 A, EE3110 **W** | EE5112, EE5143, EE5141 | three `MISSING_PREREQUISITE` EE3110 (W does not count) |
| others | none | any constrained course | one `NO_COMPLETION_RECORD` each |

```bash
mvn exec:java -Dexec.args="--input data/prerequisite-warnings --output output/prereq"
```

`PrerequisiteWarningsDatasetTest` (Java) and `tests/parity.test.mjs` (portal) both
check this dataset, and the parity test checks that both implementations produce
identical warnings.
