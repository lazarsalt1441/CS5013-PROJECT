package in.ac.iitm.slotbook.testing;

import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.CoursePair;
import in.ac.iitm.slotbook.model.InstructorPreference;
import in.ac.iitm.slotbook.model.TimeGrid;
import in.ac.iitm.slotbook.model.TimetableProblem;

import java.util.HashMap;
import java.util.Map;

/**
 * Independent re-implementation of the documented objective, used as a test oracle.
 * It deliberately does not reuse solver code so that a modelling bug shows up as a mismatch; it shares only
 * the {@link TimeGrid} overlap rules, which {@code TestingFrameworkTest} checks against hand-computed cases.
 */
public final class PenaltyCalculator {
    public static final int DEFAULT_INSTRUCTOR_PENALTY = 1;
    public static final int HISTORICAL_CHANGE_PENALTY = 2;
    public static final int STUDENT_CONFLICT_MULTIPLIER = 3;

    private PenaltyCalculator() {}

    /** Objective broken into its three documented components. */
    public record Breakdown(long instructor, long historical, long student) {
        public long total() {
            return instructor + historical + student;
        }
    }

    public static Breakdown of(TimetableProblem problem, Map<String, String> assignments) {
        return of(problem, assignments, problem.studentPairWeights());
    }

    /** Variant that reuses precomputed pair weights for callers evaluating many assignments. */
    public static Breakdown of(
            TimetableProblem problem, Map<String, String> assignments, Map<CoursePair, Integer> pairWeights) {
        Map<String, Integer> preferences = new HashMap<>();
        for (InstructorPreference preference : problem.instructorPreferences()) {
            preferences.put(preference.instructor() + "\u0000" + preference.slotId(), preference.penalty());
        }

        long instructor = 0;
        long historical = 0;
        for (Course course : problem.courses()) {
            String slot = assignments.get(course.id());
            instructor += preferences.getOrDefault(
                    course.instructor() + "\u0000" + slot, DEFAULT_INSTRUCTOR_PENALTY);
            if (!course.historicalSlot().isBlank() && !course.historicalSlot().equals(slot)) {
                historical += HISTORICAL_CHANGE_PENALTY;
            }
        }

        long student = 0;
        TimeGrid grid = problem.timeGrid();
        Map<String, Course> courses = problem.coursesById();
        for (Map.Entry<CoursePair, Integer> entry : pairWeights.entrySet()) {
            Course first = courses.get(entry.getKey().first());
            Course second = courses.get(entry.getKey().second());
            int overlaps = grid.partOverlaps(
                    first, assignments.get(first.id()), second, assignments.get(second.id()));
            student += (long) entry.getValue() * overlaps * STUDENT_CONFLICT_MULTIPLIER;
        }
        return new Breakdown(instructor, historical, student);
    }
}
