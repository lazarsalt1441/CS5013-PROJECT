# Structured Debugging Practice

1. **Reproduce:** Reduce the failure to one command or the smallest CSV fixture.
2. **Observe:** Capture status, stack trace, input row, solver diagnostics, and expected behaviour.
3. **Hypothesize:** List plausible causes before editing code.
4. **Isolate:** Determine whether ownership lies in parsing, validation, modelling, explanation, export, or UI.
5. **Correct:** Make the smallest change at the owning boundary.
6. **Test:** Add or update a focused regression test.
7. **Regress:** Run the complete suite and the feasible/infeasible CLI demonstrations.
8. **Document:** Record non-trivial failures in the prompt/debugging log and commit the evidence with the fix.

## Example from development

An exporter test expected the phrase `Alternative slots`, which belongs to the text-view formatter. The CSV exporter intentionally stores alternatives as structured column data and therefore omitted the heading. Inspection showed the export was correct and the test crossed a module boundary. The assertion was changed to verify semantic alternative evidence (`rejected because`), and the full 11-test suite was rerun.
