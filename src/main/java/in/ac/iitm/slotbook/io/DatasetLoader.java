package in.ac.iitm.slotbook.io;

import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.HardConflict;
import in.ac.iitm.slotbook.model.InstructorPreference;
import in.ac.iitm.slotbook.model.Slot;
import in.ac.iitm.slotbook.model.StudentSelection;
import in.ac.iitm.slotbook.model.TimetableProblem;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads one dataset directory containing the five documented CSV files. */
public final class DatasetLoader {

    public TimetableProblem load(Path directory) throws IOException {
        return new TimetableProblem(
                loadCourses(directory.resolve("courses.csv")),
                loadSlots(directory.resolve("slots.csv")),
                loadConflicts(directory.resolve("hard_conflicts.csv")),
                loadInstructorPreferences(directory.resolve("instructor_preferences.csv")),
                loadStudentSelections(directory.resolve("student_preferences.csv")));
    }

    private List<Course> loadCourses(Path path) throws IOException {
        return CsvSupport.read(path).stream().map(row -> {
            try {
                return new Course(
                        CsvSupport.required(row, "course_id", path),
                        CsvSupport.required(row, "title", path),
                        CsvSupport.required(row, "semester_group", path),
                        CsvSupport.required(row, "course_type", path),
                        CsvSupport.required(row, "instructor", path),
                        Integer.parseInt(CsvSupport.required(row, "class_size", path)),
                        row.getOrDefault("historical_slot", ""),
                        row.getOrDefault("fixed_slot", ""),
                        splitSet(CsvSupport.required(row, "allowed_slots", path)),
                        row.getOrDefault("additional_slot", ""),
                        row.getOrDefault("room", ""));
            } catch (IOException | NumberFormatException error) {
                throw new RowParseException(error);
            }
        }).toList();
    }

    private List<Slot> loadSlots(Path path) throws IOException {
        return CsvSupport.read(path).stream().map(row -> {
            try {
                return new Slot(
                        CsvSupport.required(row, "slot_id", path),
                        CsvSupport.required(row, "days", path),
                        CsvSupport.required(row, "start_times", path),
                        row.getOrDefault("lengths", ""),
                        CsvSupport.required(row, "category", path));
            } catch (IOException error) {
                throw new RowParseException(error);
            }
        }).toList();
    }

    private List<HardConflict> loadConflicts(Path path) throws IOException {
        return CsvSupport.read(path).stream().map(row -> {
            try {
                return new HardConflict(
                        CsvSupport.required(row, "course_1", path),
                        CsvSupport.required(row, "course_2", path),
                        CsvSupport.required(row, "reason", path));
            } catch (IOException error) {
                throw new RowParseException(error);
            }
        }).toList();
    }

    private List<InstructorPreference> loadInstructorPreferences(Path path) throws IOException {
        return CsvSupport.read(path).stream().map(row -> {
            try {
                return new InstructorPreference(
                        CsvSupport.required(row, "instructor", path),
                        CsvSupport.required(row, "slot", path),
                        Integer.parseInt(CsvSupport.required(row, "penalty", path)),
                        CsvSupport.required(row, "reason", path));
            } catch (IOException | NumberFormatException error) {
                throw new RowParseException(error);
            }
        }).toList();
    }

    private List<StudentSelection> loadStudentSelections(Path path) throws IOException {
        return CsvSupport.read(path).stream().map(row -> {
            try {
                return new StudentSelection(
                        CsvSupport.required(row, "student_id", path),
                        CsvSupport.required(row, "course_id", path),
                        Integer.parseInt(CsvSupport.required(row, "rank", path)));
            } catch (IOException | NumberFormatException error) {
                throw new RowParseException(error);
            }
        }).toList();
    }

    private Set<String> splitSet(String value) {
        return new LinkedHashSet<>(Arrays.asList(value.split("\\|")));
    }

    private static final class RowParseException extends RuntimeException {
        private RowParseException(Exception cause) {
            super(cause);
        }
    }
}

