package in.ac.iitm.slotbook.server;

import in.ac.iitm.slotbook.model.AssignmentExplanation;
import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.service.SlotbookRun;
import in.ac.iitm.slotbook.service.SlotbookService;
import in.ac.iitm.slotbook.solver.SolverChoice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Turns one portal request into a published-run reply, using the same service, validator and
 * explanations as the CLI. Independent of HTTP so it can be tested directly.
 */
public final class SolveEndpoint {

    /** HTTP status and JSON body. */
    public record Reply(int status, String json) {}

    private final Path base;
    private final double timeLimitSeconds;

    public SolveEndpoint(Path base, double timeLimitSeconds) {
        this.base = base;
        this.timeLimitSeconds = timeLimitSeconds;
    }

    /** One solve at a time: the free host has one small CPU and the solver uses all of it. */
    public synchronized Reply solve(String requestBody) {
        Path work = null;
        try {
            Object parsed = Json.parse(requestBody);
            if (!(parsed instanceof Map<?, ?> payload)) throw new BadRequestException("The request body must be a JSON object");
            SolverChoice choice = "cpsat".equals(payload.get("solver")) ? SolverChoice.CPSAT : SolverChoice.UNITIME;

            work = Files.createTempDirectory("slotbook-");
            PortalDataset.Summary merged = PortalDataset.prepare(base, work, payload);
            SlotbookRun run = new SlotbookService(choice, timeLimitSeconds).run(work);

            if (!run.solution().hasSolution()) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("ok", false);
                body.put("status", run.solution().status().toString());
                body.put("error", "No slotbook: " + String.join("; ", run.solution().diagnostics()));
                body.put("diagnostics", run.solution().diagnostics());
                return new Reply(422, Json.write(body));
            }
            return new Reply(200, Json.write(success(run, choice, merged)));
        } catch (IllegalArgumentException | BadRequestException bad) {
            return error(400, bad.getMessage());
        } catch (IOException invalid) {
            // Raised by the dataset loader for rows it cannot read; the message names the file and line.
            return error(422, invalid.getMessage());
        } catch (RuntimeException fault) {
            fault.printStackTrace(System.err);
            return error(500, "The solver failed; see the service log.");
        } finally {
            deleteQuietly(work);
        }
    }

    private static Map<String, Object> success(SlotbookRun run, SolverChoice choice, PortalDataset.Summary merged) {
        List<Map<String, Object>> slotbook = new ArrayList<>();
        List<Map<String, Object>> explanations = new ArrayList<>();
        for (Course course : run.problem().courses()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("course_id", course.id());
            row.put("title", course.title());
            row.put("semester_group", course.semesterGroup());
            row.put("course_type", course.courseType());
            row.put("instructor", course.instructor());
            row.put("assigned_slot", run.solution().assignments().getOrDefault(course.id(), ""));
            row.put("additional_slot", course.additionalSlot() == null ? "" : course.additionalSlot());
            row.put("room", course.room() == null ? "" : course.room());
            row.put("class_size", course.classSize());
            slotbook.add(row);

            AssignmentExplanation explanation = run.solution().explanations().get(course.id());
            if (explanation == null) continue;
            Map<String, Object> why = new LinkedHashMap<>();
            why.put("course_id", course.id());
            why.put("assigned_slot", explanation.assignedSlot());
            // The same " | " joining as assignment_explanations.csv, so both publishing routes match.
            why.put("reasons", String.join(" | ", explanation.reasons()));
            why.put("rejected_or_feasible_alternatives", String.join(" | ", explanation.alternatives()));
            explanations.add(why);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("solver", choice.flag());
        body.put("status", run.solution().status().toString());
        body.put("objective", run.solution().objectiveValue());
        body.put("runtime_seconds", run.solution().runtimeSeconds());
        body.put("merged_student_rows", merged.studentRows());
        body.put("merged_instructor_rows", merged.instructorRows());
        body.put("prerequisite_warnings", run.prerequisitesChecked() ? run.prerequisiteWarnings().size() : 0);
        body.put("slotbook", slotbook);
        body.put("explanations", explanations);
        return body;
    }

    static Reply error(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("error", message == null ? "Request failed" : message);
        return new Reply(status, Json.write(body));
    }

    private static void deleteQuietly(Path directory) {
        if (directory == null) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // A leftover temporary directory is harmless; the solve result stands.
        }
    }
}
