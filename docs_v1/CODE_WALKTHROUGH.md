# Code Walkthrough

## End-to-end call path

1. `SlotbookCli` or `SlotbookApplication` receives a dataset directory.
2. `SlotbookService` coordinates one run.
3. `DatasetLoader` parses five CSV files into immutable model records.
4. `InputValidator` checks identifiers, domains, ranks, duplicates, and obvious contradictions.
5. `OrToolsTimetableSolver` constructs and solves the CP-SAT model.
6. `ExplanationBuilder` derives reasons and alternative classifications from the solved assignment.
7. `SlotbookExporter` writes assignment and explanation CSV files.

## Mathematical model

For course `c` and slot `s`, the binary variable `x[c,s]` equals one exactly when that assignment is selected.

### Exactly one primary slot

```text
sum_s x[c,s] = 1
```

### Fixed and prohibited assignments

```text
x[c,fixed(c)] = 1
x[c,s] = 0 for each s outside allowed(c)
```

### Hard course conflict

```text
x[c1,s] + x[c2,s] <= 1
```

### Instructor non-overlap

For every instructor and slot, at most one of that instructor's courses can use the slot.

### Student conflict indicator

`y[pair,s]` is the logical AND of the two course-assignment variables. Three inequalities enforce equivalence:

```text
y <= x1
y <= x2
x1 + x2 - y <= 1
```

The objective minimizes instructor penalties, historical movement costs, and weighted student pair clashes.

## Explanation logic

The explanation module does not inspect internal CP-SAT search heuristics. Instead, it reports facts that can be verified from the input and solution. For each alternative slot it checks, in order:

1. Is the slot allowed?
2. Does it contradict a fixed assignment?
3. Would it violate a hard course conflict in the current solution?
4. Would it double-book the instructor?
5. If feasible, what local instructor, stability, and student-conflict cost would it have?

This distinction matters: a feasible alternative's displayed cost is explicitly labelled a local estimate, not a claim that the entire timetable would remain globally optimal.

## Safe live modifications

- New CSV validation: modify `InputValidator` and add a focused test.
- New hard relation: modify `OrToolsTimetableSolver`, then test the invariant rather than one exact schedule.
- New output column: update `SlotbookExporter` and its export test.
- New explanation wording: update `ExplanationBuilder`; do not change solver logic from the UI.
- New UI field: update only `SlotbookApplication` or its view record.
