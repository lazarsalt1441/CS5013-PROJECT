package in.ac.iitm.slotbook.solver;

import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.InstructorPreference;
import in.ac.iitm.slotbook.model.TimetableProblem;

import java.util.HashMap;
import java.util.Map;

/**
 * The slotbook objective shared by every solver:
 * per-course instructor penalty (default 1) + historical-move penalty,
 * plus student pair weight x overlapping parts x {@link #STUDENT_CONFLICT_MULTIPLIER}.
 */
public final class Objective {
    public static final int DEFAULT_INSTRUCTOR_PENALTY = 1;
    public static final int HISTORICAL_CHANGE_PENALTY = 2;
    public static final int STUDENT_CONFLICT_MULTIPLIER = 3;

    private final Map<String, Integer> preferences = new HashMap<>();

    public Objective(TimetableProblem problem) {
        for (InstructorPreference preference : problem.instructorPreferences()) {
            preferences.put(key(preference.instructor(), preference.slotId()), preference.penalty());
        }
    }

    public int instructorPenalty(Course course, String slotId) {
        return preferences.getOrDefault(key(course.instructor(), slotId), DEFAULT_INSTRUCTOR_PENALTY);
    }

    public int historicalPenalty(Course course, String slotId) {
        return !course.historicalSlot().isBlank() && !course.historicalSlot().equals(slotId)
                ? HISTORICAL_CHANGE_PENALTY : 0;
    }

    /** Instructor plus historical penalty for placing the course's lecture in the slot. */
    public int coursePenalty(Course course, String slotId) {
        return instructorPenalty(course, slotId) + historicalPenalty(course, slotId);
    }

    private static String key(String instructor, String slotId) {
        return instructor + "\u0000" + slotId;
    }
}
