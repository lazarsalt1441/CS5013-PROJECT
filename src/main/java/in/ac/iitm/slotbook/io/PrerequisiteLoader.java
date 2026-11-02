package in.ac.iitm.slotbook.io;

import in.ac.iitm.slotbook.model.CompletedCourse;
import in.ac.iitm.slotbook.model.Prerequisite;
import in.ac.iitm.slotbook.model.PrerequisiteData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the optional {@code prerequisites.csv} and {@code completed_courses.csv} exported by the portal.
 * Headers: {@code course_id,prereq_course_id} and {@code student_id,course_id,grade[,term][,source]}.
 */
public final class PrerequisiteLoader {

    public PrerequisiteData load(PrerequisiteInputs inputs) throws IOException {
        return new PrerequisiteData(
                inputs.prerequisites() == null ? List.of() : loadPrerequisites(inputs.prerequisites()),
                inputs.completedCourses() == null ? List.of() : loadCompletions(inputs.completedCourses()));
    }

    List<Prerequisite> loadPrerequisites(Path path) throws IOException {
        List<Prerequisite> rules = new ArrayList<>();
        for (Map<String, String> row : read(path)) {
            rules.add(new Prerequisite(
                    upper(CsvSupport.required(row, "course_id", path)),
                    upper(CsvSupport.required(row, "prereq_course_id", path))));
        }
        return rules;
    }

    List<CompletedCourse> loadCompletions(Path path) throws IOException {
        List<CompletedCourse> completions = new ArrayList<>();
        for (Map<String, String> row : read(path)) {
            String source = row.getOrDefault("source", "");
            completions.add(new CompletedCourse(
                    upper(CsvSupport.required(row, "student_id", path)),
                    upper(CsvSupport.required(row, "course_id", path)),
                    upper(CsvSupport.required(row, "grade", path)),
                    row.getOrDefault("term", ""),
                    source.isBlank() ? "grade_card" : source));
        }
        return completions;
    }

    private List<Map<String, String>> read(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new NoSuchFileException(path.toString(), null, "prerequisite input not found");
        return CsvSupport.read(path);
    }

    private static String upper(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
