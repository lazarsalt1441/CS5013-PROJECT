package in.ac.iitm.slotbook.io;

import in.ac.iitm.slotbook.model.CompletedCourse;
import in.ac.iitm.slotbook.model.Prerequisite;
import in.ac.iitm.slotbook.model.PrerequisiteData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading the optional prerequisite CSV files exported by the portal. */
class PrerequisiteLoaderTest {

    private static Path write(Path dir, String name, String content) throws IOException {
        return Files.writeString(dir.resolve(name), content);
    }

    @Test
    void readsBothFilesAndNormalisesCase(@TempDir Path dir) throws Exception {
        write(dir, "prerequisites.csv", "course_id,prereq_course_id\nee5141,EE3110\n");
        write(dir, "completed_courses.csv", "student_id,course_id,grade,term,source\ns001,ee3110,b,05,grade_card\n");
        PrerequisiteData data = new PrerequisiteLoader().load(PrerequisiteInputs.fromDirectory(dir));
        assertEquals(List.of(new Prerequisite("EE5141", "EE3110")), data.prerequisites());
        assertEquals(List.of(new CompletedCourse("S001", "EE3110", "B", "05", "grade_card")), data.completions());
    }

    @Test
    void termAndSourceColumnsAreOptional(@TempDir Path dir) throws Exception {
        Path file = write(dir, "done.csv", "student_id,course_id,grade\nS1,EE1101,A\n");
        PrerequisiteData data = new PrerequisiteLoader().load(new PrerequisiteInputs(null, file));
        assertEquals(new CompletedCourse("S1", "EE1101", "A", "", "grade_card"), data.completions().get(0));
        assertTrue(data.hasNoRules());
    }

    @Test
    void absentOptionalFilesMeanNoInputs(@TempDir Path dir) throws Exception {
        PrerequisiteInputs inputs = PrerequisiteInputs.fromDirectory(dir);
        assertNull(inputs.prerequisites());
        assertNull(inputs.completedCourses());
        assertEquals(PrerequisiteData.empty(), new PrerequisiteLoader().load(inputs));
        assertEquals(PrerequisiteData.empty(), new PrerequisiteLoader().load(PrerequisiteInputs.none()));
    }

    @Test
    void explicitPathsOverrideTheDatasetDirectory(@TempDir Path dir) throws Exception {
        write(dir, "prerequisites.csv", "course_id,prereq_course_id\nEE1,EE2\n");
        Path other = write(dir, "other.csv", "course_id,prereq_course_id\nEE3,EE4\n");
        PrerequisiteInputs inputs = PrerequisiteInputs.fromDirectory(dir).overriddenBy(other, null);
        assertEquals(other, inputs.prerequisites());
        assertEquals("EE3", new PrerequisiteLoader().load(inputs).prerequisites().get(0).courseId());
    }

    @Test
    void aMissingRequiredValueIsAnError(@TempDir Path dir) throws Exception {
        Path file = write(dir, "prerequisites.csv", "course_id,prereq_course_id\nEE5141,\n");
        IOException error = assertThrows(IOException.class,
                () -> new PrerequisiteLoader().load(new PrerequisiteInputs(file, null)));
        assertTrue(error.getMessage().contains("prereq_course_id"));
    }

    @Test
    void aNamedFileThatDoesNotExistIsAnError(@TempDir Path dir) {
        assertThrows(NoSuchFileException.class,
                () -> new PrerequisiteLoader().load(new PrerequisiteInputs(dir.resolve("missing.csv"), null)));
    }
}
