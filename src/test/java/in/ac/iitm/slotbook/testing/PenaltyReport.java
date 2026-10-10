package in.ac.iitm.slotbook.testing;

import in.ac.iitm.slotbook.model.TimetableSolution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Collects one row per scenario comparing the UniTime and CP-SAT solvers, and renders it as a
 * console table and a CSV file. The breakdown columns describe the UniTime timetable.
 */
public final class PenaltyReport {
    public record Row(
            String scenario,
            int courses,
            int students,
            int conflicts,
            PenaltyCalculator.Breakdown uniTimeBreakdown,
            String uniTimeStatus,
            long uniTimeTotal,
            double uniTimeSeconds,
            String cpSatStatus,
            long cpSatTotal,
            double cpSatSeconds,
            long plantedPenalty) {

        /** UniTime total minus CP-SAT total; zero means UniTime matched it. */
        public long gap() {
            return uniTimeTotal - cpSatTotal;
        }
    }

    private final List<Row> rows = new ArrayList<>();

    public synchronized void add(Scenario scenario, TimetableSolution uniTime, TimetableSolution cpSat) {
        rows.add(new Row(
                scenario.name(),
                scenario.problem().courses().size(),
                (int) scenario.problem().studentSelections().stream().map(s -> s.studentId()).distinct().count(),
                scenario.problem().hardConflicts().size(),
                PenaltyCalculator.of(scenario.problem(), uniTime.assignments()),
                uniTime.status().name(),
                uniTime.objectiveValue(),
                uniTime.runtimeSeconds(),
                cpSat.status().name(),
                cpSat.objectiveValue(),
                cpSat.runtimeSeconds(),
                PenaltyCalculator.of(scenario.problem(), scenario.planted()).total()));
    }

    public synchronized List<Row> rows() {
        return List.copyOf(rows);
    }

    public synchronized String table() {
        StringBuilder out = new StringBuilder();
        String format = "%-20s %7s %8s %9s | %10s %10s %7s | %8s %8s %7s | %8s %8s %7s | %4s %7s%n";
        out.append(String.format(format, "SCENARIO", "COURSES", "STUDENTS", "CONFLICTS",
                "INSTRUCTOR", "HISTORICAL", "STUDENT",
                "UNITIME", "STATUS", "TIME_S", "CPSAT", "STATUS", "TIME_S", "GAP", "PLANTED"));
        for (Row r : sorted()) {
            out.append(String.format(format, r.scenario(), r.courses(), r.students(), r.conflicts(),
                    r.uniTimeBreakdown().instructor(), r.uniTimeBreakdown().historical(),
                    r.uniTimeBreakdown().student(),
                    r.uniTimeTotal(), r.uniTimeStatus(), String.format("%.3f", r.uniTimeSeconds()),
                    r.cpSatTotal(), r.cpSatStatus(), String.format("%.3f", r.cpSatSeconds()),
                    r.gap(), r.plantedPenalty()));
        }
        return out.toString();
    }

    public synchronized void writeCsv(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        List<String> lines = new ArrayList<>(List.of(
                "scenario,courses,students,conflicts,instructor_penalty,historical_penalty,student_penalty,"
                        + "unitime_total,unitime_status,unitime_seconds,cpsat_total,cpsat_status,cpsat_seconds,"
                        + "gap,planted_penalty"));
        for (Row r : sorted()) {
            lines.add(String.join(",", r.scenario(), Integer.toString(r.courses()), Integer.toString(r.students()),
                    Integer.toString(r.conflicts()), Long.toString(r.uniTimeBreakdown().instructor()),
                    Long.toString(r.uniTimeBreakdown().historical()), Long.toString(r.uniTimeBreakdown().student()),
                    Long.toString(r.uniTimeTotal()), r.uniTimeStatus(), String.format("%.3f", r.uniTimeSeconds()),
                    Long.toString(r.cpSatTotal()), r.cpSatStatus(), String.format("%.3f", r.cpSatSeconds()),
                    Long.toString(r.gap()), Long.toString(r.plantedPenalty())));
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private List<Row> sorted() {
        List<Row> copy = new ArrayList<>(rows);
        copy.sort(Comparator.comparingInt(r -> order(r.scenario())));
        return copy;
    }

    private int order(String scenario) {
        // Keep the catalogue order: named scenarios first, then random-N by number.
        if (scenario.matches("random-\\d+")) return 1000 + Integer.parseInt(scenario.substring(7));
        return rows.stream().map(Row::scenario).toList().indexOf(scenario);
    }
}
