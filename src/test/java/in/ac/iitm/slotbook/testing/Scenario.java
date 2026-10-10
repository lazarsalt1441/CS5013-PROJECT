package in.ac.iitm.slotbook.testing;

import in.ac.iitm.slotbook.model.TimetableProblem;

import java.util.Map;

/**
 * A generated test problem.
 *
 * @param planted an assignment known to satisfy every hard constraint, so the problem is feasible
 *                and the solver's objective can never exceed the planted assignment's penalty
 */
public record Scenario(String name, String description, TimetableProblem problem, Map<String, String> planted) {
    public Scenario {
        planted = Map.copyOf(planted);
    }

    @Override
    public String toString() {
        return name;
    }
}
