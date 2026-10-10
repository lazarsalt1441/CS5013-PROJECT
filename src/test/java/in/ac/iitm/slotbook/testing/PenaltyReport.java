package in.ac.iitm.slotbook.testing;

import in.ac.iitm.slotbook.model.TimetableSolution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Collects one row per solved scenario and renders it as a console table and a CSV file. */
public final class PenaltyReport {
    public record Row(
            String scenario,
            int courses,
            int students,
            int conflicts,
            String status,
            long objective,
            PenaltyCalculator.Breakdown breakdown,
            long plantedPenalty,
            double runtimeSeconds) {}

    private final List<Row> rows = new ArrayList<>();

    public synchronized void add(Scenario scenario, TimetableSolution solution) {
        rows.add(new Row(
                scenario.name(),
                scenario.problem().courses().size(),
                (int) scenario.problem().studentSelections().stream().map(s -> s.studentId()).distinct().count(),
                scenario.problem().hardConflicts().size(),
                solution.status().name(),
                solution.objectiveValue(),
                PenaltyCalculator.of(scenario.problem(), solution.assignments()),
                PenaltyCalculator.of(scenario.problem(), scenario.planted()).total(),
                solution.runtimeSeconds()));
    }

    public synchronized String table() {
        StringBuilder out = new StringBuilder();
        String format = "%-20s %7s %8s %9s %-8s %10s %10s %10s %8s %8s %7s%n";
        out.append(String.format(format, "SCENARIO", "COURSES", "STUDENTS", "CONFLICTS", "STATUS",
                "INSTRUCTOR", "HISTORICAL", "STUDENT", "TOTAL", "PLANTED", "TIME_S"));
        for (Row r : rows) {
            out.append(String.format(format, r.scenario(), r.courses(), r.students(), r.conflicts(), r.status(),
                    r.breakdown().instructor(), r.breakdown().historical(), r.breakdown().student(),
                    r.objective(), r.plantedPenalty(), String.format("%.3f", r.runtimeSeconds())));
        }
        return out.toString();
    }

    public synchronized void writeCsv(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        List<String> lines = new ArrayList<>(List.of(
                "scenario,courses,students,conflicts,status,instructor_penalty,historical_penalty,"
                        + "student_penalty,total_penalty,planted_penalty,runtime_seconds"));
        for (Row r : rows) {
            lines.add(String.join(",", r.scenario(), Integer.toString(r.courses()), Integer.toString(r.students()),
                    Integer.toString(r.conflicts()), r.status(), Long.toString(r.breakdown().instructor()),
                    Long.toString(r.breakdown().historical()), Long.toString(r.breakdown().student()),
                    Long.toString(r.objective()), Long.toString(r.plantedPenalty()),
                    String.format("%.3f", r.runtimeSeconds())));
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
    }
}
