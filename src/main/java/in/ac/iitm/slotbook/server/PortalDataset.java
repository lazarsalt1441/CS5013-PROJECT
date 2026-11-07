package in.ac.iitm.slotbook.server;

import in.ac.iitm.slotbook.io.CsvSupport;
import in.ac.iitm.slotbook.io.PrerequisiteInputs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Builds the dataset the service solves: a copy of the base dataset (the one the portal's course
 * catalog comes from) with the portal's submissions merged in. A student's or instructor's portal
 * rows replace all of that person's base rows; everyone else keeps their base rows. This is the
 * same merge {@code scripts/live-demo.sh} does for the manual workflow.
 */
public final class PortalDataset {
    static final String STUDENTS = "student_preferences.csv";
    static final String INSTRUCTORS = "instructor_preferences.csv";

    /** What was merged, reported back to the admin. */
    public record Summary(int studentRows, int instructorRows, boolean prerequisitesSupplied) {}

    private PortalDataset() {}

    /**
     * Copies {@code base} into the empty directory {@code target} and merges {@code payload}'s
     * {@code student_preferences_csv}, {@code instructor_preferences_csv}, {@code prerequisites_csv}
     * and {@code completed_courses_csv} (each optional).
     */
    public static Summary prepare(Path base, Path target, Map<?, ?> payload) throws IOException {
        try (Stream<Path> files = Files.list(base)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (Files.isRegularFile(file) && file.getFileName().toString().endsWith(".csv")) {
                    Files.copy(file, target.resolve(file.getFileName().toString()));
                }
            }
        }
        int students = merge(target.resolve(STUDENTS), text(payload, "student_preferences_csv"));
        int instructors = merge(target.resolve(INSTRUCTORS), text(payload, "instructor_preferences_csv"));
        boolean prerequisites = writeIfRows(target.resolve(PrerequisiteInputs.PREREQUISITES_FILE),
                text(payload, "prerequisites_csv"));
        boolean completions = writeIfRows(target.resolve(PrerequisiteInputs.COMPLETED_COURSES_FILE),
                text(payload, "completed_courses_csv"));
        return new Summary(students, instructors, prerequisites && completions);
    }

    /**
     * Merges portal CSV text into {@code file} and returns the number of portal rows merged.
     * The portal header must match the file's header column for column.
     */
    static int merge(Path file, String portalCsv) throws IOException {
        List<String> portal = dataLines(portalCsv);
        if (portal.size() < 2) return 0; // nothing submitted: leave the file exactly as it was
        List<String> base = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (base.isEmpty()) throw new IOException(file.getFileName() + " in the base dataset is empty");
        List<String> expected = columns(base.get(0));
        List<String> received = columns(portal.get(0));
        if (!expected.equals(received)) {
            throw new BadRequestException(file.getFileName() + " from the portal has columns " + received
                    + "; expected " + expected);
        }
        Set<String> replaced = new HashSet<>();
        List<String> portalRows = portal.subList(1, portal.size());
        for (String row : portalRows) replaced.add(key(row));

        List<String> merged = new ArrayList<>();
        merged.add(base.get(0));
        for (String row : base.subList(1, base.size())) {
            if (!row.isBlank() && !replaced.contains(key(row))) merged.add(row);
        }
        merged.addAll(portalRows);
        Files.write(file, merged, StandardCharsets.UTF_8);
        return portalRows.size();
    }

    private static boolean writeIfRows(Path file, String csv) throws IOException {
        List<String> lines = dataLines(csv);
        if (lines.size() < 2) return false;
        Files.write(file, lines, StandardCharsets.UTF_8);
        return true;
    }

    /** Header plus non-blank rows; empty if there is no text. */
    private static List<String> dataLines(String csv) {
        List<String> lines = new ArrayList<>();
        if (csv == null) return lines;
        for (String line : csv.split("\\r?\\n")) {
            if (!line.isBlank()) lines.add(line);
        }
        return lines;
    }

    private static List<String> columns(String header) {
        List<String> columns = new ArrayList<>();
        for (String column : CsvSupport.parseLine(header)) columns.add(column.trim());
        return columns;
    }

    private static String key(String row) {
        return CsvSupport.parseLine(row).get(0).trim();
    }

    private static String text(Map<?, ?> payload, String field) {
        Object value = payload.get(field);
        if (value == null) return null;
        if (value instanceof String s) return s;
        throw new BadRequestException(field + " must be a string of CSV text");
    }
}
