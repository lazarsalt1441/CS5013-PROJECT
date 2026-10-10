package in.ac.iitm.slotbook.testing;

import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.TimetableProblem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Writes a generated problem in the five-CSV dataset layout so the CLI and UI can load it. */
public final class ScenarioCsvWriter {
    private ScenarioCsvWriter() {}

    public static void write(TimetableProblem problem, Path directory) throws IOException {
        Files.createDirectories(directory);

        List<String> courses = new ArrayList<>(List.of(
                "course_id,title,semester_group,course_type,instructor,class_size,historical_slot,"
                        + "fixed_slot,allowed_slots,additional_slot,room"));
        for (Course c : problem.courses()) {
            courses.add(row(c.id(), c.title(), c.semesterGroup(), c.courseType(), c.instructor(),
                    Integer.toString(c.classSize()), c.historicalSlot(), c.fixedSlot(),
                    String.join("|", c.allowedSlots().stream().sorted().toList()), c.additionalSlot(), c.room()));
        }
        write(directory.resolve("courses.csv"), courses);

        List<String> slots = new ArrayList<>(List.of("slot_id,days,start_times,lengths,category"));
        problem.slots().forEach(s -> slots.add(row(s.id(), s.days(), s.startTimes(), s.lengths(), s.category())));
        write(directory.resolve("slots.csv"), slots);

        List<String> conflicts = new ArrayList<>(List.of("course_1,course_2,reason"));
        problem.hardConflicts().forEach(c -> conflicts.add(row(c.course1(), c.course2(), c.reason())));
        write(directory.resolve("hard_conflicts.csv"), conflicts);

        List<String> preferences = new ArrayList<>(List.of("instructor,slot,penalty,reason"));
        problem.instructorPreferences().forEach(p -> preferences.add(
                row(p.instructor(), p.slotId(), Integer.toString(p.penalty()), p.reason())));
        write(directory.resolve("instructor_preferences.csv"), preferences);

        List<String> students = new ArrayList<>(List.of("student_id,course_id,rank"));
        problem.studentSelections().forEach(s -> students.add(
                row(s.studentId(), s.courseId(), Integer.toString(s.rank()))));
        write(directory.resolve("student_preferences.csv"), students);
    }

    private static String row(String... values) {
        List<String> cells = new ArrayList<>();
        for (String value : values) {
            String safe = value == null ? "" : value;
            cells.add(safe.contains(",") || safe.contains("\"") ? '"' + safe.replace("\"", "\"\"") + '"' : safe);
        }
        return String.join(",", cells);
    }

    private static void write(Path path, List<String> lines) throws IOException {
        Files.write(path, lines, StandardCharsets.UTF_8);
    }
}
