package in.ac.iitm.slotbook.io;

import in.ac.iitm.slotbook.model.AssignmentExplanation;
import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.service.SlotbookRun;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Exports the solved slotbook and full explanations as portable CSV files. */
public final class SlotbookExporter {

    public void export(SlotbookRun run, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        exportAssignments(run, outputDirectory.resolve("generated_slotbook.csv"));
        exportExplanations(run, outputDirectory.resolve("assignment_explanations.csv"));
    }

    private void exportAssignments(SlotbookRun run, Path path) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("course_id,title,semester_group,course_type,instructor,assigned_slot,additional_slot,room,class_size");
        for (Course course : run.problem().courses()) {
            String slot = run.solution().assignments().getOrDefault(course.id(), "");
            lines.add(String.join(",",
                    csv(course.id()), csv(course.title()), csv(course.semesterGroup()), csv(course.courseType()),
                    csv(course.instructor()), csv(slot), csv(course.additionalSlot()), csv(course.room()),
                    Integer.toString(course.classSize())));
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private void exportExplanations(SlotbookRun run, Path path) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("course_id,assigned_slot,reasons,rejected_or_feasible_alternatives");
        for (Course course : run.problem().courses()) {
            AssignmentExplanation explanation = run.solution().explanations().get(course.id());
            if (explanation == null) continue;
            lines.add(String.join(",",
                    csv(course.id()),
                    csv(explanation.assignedSlot()),
                    csv(String.join(" | ", explanation.reasons())),
                    csv(String.join(" | ", explanation.alternatives()))));
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }
}

