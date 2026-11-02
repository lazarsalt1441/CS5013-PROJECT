package in.ac.iitm.slotbook.model;

import java.util.Locale;
import java.util.Set;

/**
 * One course on a student's record, as exported by the portal from a grade card
 * ({@code source = grade_card}) or, in future, from academic-section records ({@code source = admin}).
 */
public record CompletedCourse(String studentId, String courseId, String grade, String term, String source) {

    /** Grades that do not complete a course; the portal applies the same rule when parsing grade cards. */
    public static final Set<String> NOT_COMPLETED_GRADES = Set.of("U", "W", "I");

    public boolean isCompleted() {
        return !NOT_COMPLETED_GRADES.contains(grade.trim().toUpperCase(Locale.ROOT));
    }
}
