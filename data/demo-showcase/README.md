# Mid-Evaluation Showcase Dataset

This synthetic scenario is small enough to explain in two minutes but rich enough to demonstrate every important scheduling trade-off.

## Story

- Two large core courses are administratively fixed to slots B and C.
- One core course has a fixed Friday laboratory and must avoid lecture choices that overlap it.
- Eight popular electives compete for seven standard lecture slots, so the model must decide which low-weight student conflict is least harmful.
- Thirty anonymous students form four recognisable preference clusters: AI/vision, communications, theory, and cross-domain electives.
- Faculty preferences deliberately disagree with some historical slots and student-demand patterns.
- Protected curriculum pairs, shared rooms, fixed courses, and the laboratory remain hard constraints.

## Expected demonstration

1. Run the validator to show that all five input files are consistent.
2. Generate the timetable with UniTime.
3. Point out the fixed B and C assignments and the Friday laboratory.
4. Open one elective explanation to show the instructor penalty, historical stability cost, student co-demand contribution, and rejected alternatives.
5. Compare with `--solver cpsat` to show that the wrapper and reference solver share the same objective.

## Commands

```bash
mvn exec:java -Dexec.args="--input data/demo-showcase --output output/demo-showcase --time-limit 10"
mvn exec:java -Dexec.args="--input data/demo-showcase --output output/demo-showcase-cpsat --solver cpsat --time-limit 10"
```

The scenario contains only synthetic student identifiers and preferences.
