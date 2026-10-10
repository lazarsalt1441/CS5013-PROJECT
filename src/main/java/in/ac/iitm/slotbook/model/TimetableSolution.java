package in.ac.iitm.slotbook.model;

import java.util.List;
import java.util.Map;

/** Solver result plus evidence required by the UI and exporter. */
public record TimetableSolution(
        SolveStatus status,
        Map<String, String> assignments,
        Map<String, AssignmentExplanation> explanations,
        List<String> diagnostics,
        long objectiveValue,
        double runtimeSeconds) {

    public TimetableSolution {
        assignments = Map.copyOf(assignments);
        explanations = Map.copyOf(explanations);
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean hasSolution() {
        return status == SolveStatus.OPTIMAL || status == SolveStatus.FEASIBLE;
    }
}

