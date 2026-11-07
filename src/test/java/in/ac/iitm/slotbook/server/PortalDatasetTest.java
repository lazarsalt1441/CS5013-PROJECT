package in.ac.iitm.slotbook.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalDatasetTest {
    private static final Path FEASIBLE = Path.of("data/feasible");

    @TempDir
    Path work;

    @Test
    void anInstructorsPortalRowsReplaceAllTheirSampleRows() throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("instructor_preferences_csv",
                "instructor,slot,penalty,reason\nPradeep K,E,40,\"Meeting, Wednesday\"\n\"New Prof\",A,1,x\n");
        PortalDataset.Summary summary = PortalDataset.prepare(FEASIBLE, work, payload);

        assertEquals(2, summary.instructorRows());
        List<String> merged = Files.readAllLines(work.resolve(PortalDataset.INSTRUCTORS));
        List<String> pradeep = merged.stream().filter(line -> line.startsWith("Pradeep K")).toList();
        assertEquals(List.of("Pradeep K,E,40,\"Meeting, Wednesday\""), pradeep);
        assertTrue(merged.contains("\"New Prof\",A,1,x"));
        long otherBefore = Files.readAllLines(FEASIBLE.resolve(PortalDataset.INSTRUCTORS)).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("Pradeep K")).count();
        assertEquals(otherBefore + 2, merged.size(), "everyone else keeps their rows; the header is kept once");
    }

    @Test
    void noPortalRowsLeavesTheBaseDatasetUnchanged() throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("student_preferences_csv", "student_id,course_id,rank\n");
        payload.put("instructor_preferences_csv", "");
        PortalDataset.Summary summary = PortalDataset.prepare(FEASIBLE, work, payload);

        assertEquals(0, summary.studentRows());
        for (String file : List.of("courses.csv", "slots.csv", PortalDataset.STUDENTS, PortalDataset.INSTRUCTORS)) {
            assertEquals(Files.readAllLines(FEASIBLE.resolve(file)), Files.readAllLines(work.resolve(file)), file);
        }
    }

    @Test
    void aStudentsRowsReplaceOnlyThatStudent() throws IOException {
        String firstStudent = Files.readAllLines(FEASIBLE.resolve(PortalDataset.STUDENTS)).get(1).split(",")[0];
        Map<String, Object> payload = new HashMap<>();
        payload.put("student_preferences_csv", "student_id,course_id,rank\r\n" + firstStudent + ",EE1101,1\r\n");
        PortalDataset.prepare(FEASIBLE, work, payload);

        List<String> rows = Files.readAllLines(work.resolve(PortalDataset.STUDENTS)).stream()
                .filter(line -> line.startsWith(firstStudent + ",")).toList();
        assertEquals(List.of(firstStudent + ",EE1101,1"), rows);
    }

    @Test
    void prerequisiteFilesAreWrittenOnlyWhenTheyHaveRows() throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("prerequisites_csv", "course_id,prereq_course_id\nEE1101,EE1100\n");
        payload.put("completed_courses_csv", "student_id,course_id,grade,term,source\n");
        PortalDataset.Summary summary = PortalDataset.prepare(FEASIBLE, work, payload);

        assertTrue(Files.exists(work.resolve("prerequisites.csv")));
        assertFalse(Files.exists(work.resolve("completed_courses.csv")));
        assertFalse(summary.prerequisitesSupplied());
    }

    @Test
    void aHeaderOnlyUploadStillChecksNothingAndChangesNothing() throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("student_preferences_csv", "something,else\n");
        assertEquals(0, PortalDataset.prepare(FEASIBLE, work, payload).studentRows());
    }

    @Test
    void aHeaderThatDiffersFromTheDatasetIsRejected() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("student_preferences_csv", "student,course_id,rank\nX,EE1101,1\n");
        BadRequestException error = assertThrows(BadRequestException.class, () -> PortalDataset.prepare(FEASIBLE, work, payload));
        assertTrue(error.getMessage().contains("expected [student_id, course_id, rank]"), error.getMessage());
    }

    @Test
    void aNonStringFieldIsRejected() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("student_preferences_csv", List.of("not", "csv"));
        assertThrows(BadRequestException.class, () -> PortalDataset.prepare(FEASIBLE, work, payload));
    }
}
