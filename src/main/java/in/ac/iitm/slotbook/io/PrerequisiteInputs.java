package in.ac.iitm.slotbook.io;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where to read prerequisite rules and completed courses from. Either path may be null, meaning
 * "not supplied". {@link #fromDirectory} picks up the optional files next to the five required ones.
 */
public record PrerequisiteInputs(Path prerequisites, Path completedCourses) {
    public static final String PREREQUISITES_FILE = "prerequisites.csv";
    public static final String COMPLETED_COURSES_FILE = "completed_courses.csv";

    public static PrerequisiteInputs none() {
        return new PrerequisiteInputs(null, null);
    }

    public static PrerequisiteInputs fromDirectory(Path directory) {
        return new PrerequisiteInputs(
                existing(directory.resolve(PREREQUISITES_FILE)),
                existing(directory.resolve(COMPLETED_COURSES_FILE)));
    }

    /** Replaces either path where an override is given (CLI options win over the dataset directory). */
    public PrerequisiteInputs overriddenBy(Path prerequisitesOverride, Path completedOverride) {
        return new PrerequisiteInputs(
                prerequisitesOverride != null ? prerequisitesOverride : prerequisites,
                completedOverride != null ? completedOverride : completedCourses);
    }

    private static Path existing(Path path) {
        return Files.isRegularFile(path) ? path : null;
    }
}
