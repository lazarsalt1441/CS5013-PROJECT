package in.ac.iitm.slotbook.testing;

import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.CoursePair;
import in.ac.iitm.slotbook.model.Slot;
import in.ac.iitm.slotbook.model.TimetableProblem;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;

/** Exhaustive search over every assignment; only usable for a handful of courses. */
public final class BruteForceSolver {
    public static final int MAX_COURSES = 7;

    private BruteForceSolver() {}

    /** Minimum objective over all feasible assignments, or empty if none is feasible. */
    public static OptionalLong optimum(TimetableProblem problem) {
        if (problem.courses().size() > MAX_COURSES) {
            throw new IllegalArgumentException("Brute force is limited to " + MAX_COURSES + " courses");
        }
        Search search = new Search(problem);
        search.place(0);
        return search.best == Long.MAX_VALUE ? OptionalLong.empty() : OptionalLong.of(search.best);
    }

    private static final class Search {
        private final TimetableProblem problem;
        private final Map<CoursePair, Integer> pairWeights;
        private final Map<String, String> partial = new HashMap<>();
        private long best = Long.MAX_VALUE;

        private Search(TimetableProblem problem) {
            this.problem = problem;
            this.pairWeights = problem.studentPairWeights();
        }

        private void place(int index) {
            if (index == problem.courses().size()) {
                if (ConstraintChecker.violations(problem, partial).isEmpty()) {
                    best = Math.min(best, PenaltyCalculator.of(problem, partial, pairWeights).total());
                }
                return;
            }
            Course course = problem.courses().get(index);
            for (Slot slot : problem.slots()) {
                // Cheap per-course pruning; pairwise constraints are checked at the leaves.
                if (!course.allowedSlots().contains(slot.id())) continue;
                if (course.hasFixedSlot() && !course.fixedSlot().equals(slot.id())) continue;
                partial.put(course.id(), slot.id());
                place(index + 1);
            }
            partial.remove(course.id());
        }
    }
}
