# Verification Record

Date: 10 October 2026

## Environment

- Java/Javac: Temurin 17.0.17 for verification
- Solver: Google OR-Tools 9.11.4210
- Tests: JUnit Platform 1.11.3 / JUnit Jupiter 5.11.3

## Automated result

```text
8 containers found
8 containers started
8 containers successful
11 tests found
11 tests started
11 tests successful
0 tests failed
```

## Feasible CLI result

```text
Status: OPTIMAL
Objective: 17
Runtime: 0.433 s
Assignments produced: 18 / 18
Exports produced: generated_slotbook.csv, assignment_explanations.csv
```

The test suite separately verifies that every assignment is in its course's allowed domain, every fixed slot is respected, and every hard-conflict pair is separated.

## Negative fixture result

```text
Status: INVALID_INPUT
Diagnostic: FIXED_CONFLICT: EE9001 and EE9002 are both fixed to A
but cannot clash: Both are compulsory for the same cohort
```

## PDF verification

- Page count: 3
- Page size: A4
- Pages rendered and visually inspected: 3 / 3
- Clipping or overlap observed: none

## Remaining manual check

The JavaFX interaction smoke test must be run on a graphical desktop because the verification container is headless. Compilation of all JavaFX production sources succeeded.
