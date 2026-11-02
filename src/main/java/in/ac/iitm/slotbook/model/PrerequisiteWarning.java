package in.ac.iitm.slotbook.model;

/**
 * An advisory finding about a student's ranked courses. Warnings never block solving and never
 * change the solver input. Codes and messages match the portal's {@code api/_prerequisites.js}.
 */
public record PrerequisiteWarning(String studentId, String code, String courseId, String prerequisiteCourseId,
                                  String message) {
    public static final String MISSING = "MISSING_PREREQUISITE";
    public static final String NO_RECORD = "NO_COMPLETION_RECORD";
    public static final String SELF_PREREQUISITE = "SELF_PREREQUISITE";
}
