package in.ac.iitm.slotbook.io;

import in.ac.iitm.slotbook.model.PrerequisiteData;
import in.ac.iitm.slotbook.model.PrerequisiteWarning;
import in.ac.iitm.slotbook.model.SolveStatus;
import in.ac.iitm.slotbook.model.TimetableProblem;
import in.ac.iitm.slotbook.model.TimetableSolution;
import in.ac.iitm.slotbook.service.SlotbookRun;
import in.ac.iitm.slotbook.service.SlotbookService;
import in.ac.iitm.slotbook.validation.PrerequisiteChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The data/prerequisite-warnings fixture end to end. expected_warnings.csv is shared with the
 * portal's tests/parity.test.mjs, so both implementations are held to the same output.
 */
class PrerequisiteWarningsDatasetTest {
    private static final Path DATASET = Path.of("data/prerequisite-warnings");

    /** Records every problem it is given, so tests can see exactly what reached the solver. */
    private static final class RecordingSolver implements in.ac.iitm.slotbook.solver.TimetableSolver {
        final List<TimetableProblem> seen = new ArrayList<>();

        @Override
        public TimetableSolution solve(TimetableProblem problem) {
            seen.add(problem);
            return new TimetableSolution(SolveStatus.FEASIBLE, Map.of(), Map.of(), List.of(), 0, 0);
        }
    }

    @Test
    void javaWarningsEqualTheSharedGoldenFile() throws Exception {
        TimetableProblem problem = new DatasetLoader().load(DATASET);
        PrerequisiteData data = new PrerequisiteLoader().load(PrerequisiteInputs.fromDirectory(DATASET));
        List<PrerequisiteWarning> warnings = new PrerequisiteChecker().check(problem.studentSelections(), data);

        List<List<String>> expected = CsvSupport.read(DATASET.resolve("expected_warnings.csv")).stream()
                .map(row -> List.of(row.get("student_id"), row.get("code"), row.get("course_id"),
                        row.get("prereq_course_id"), row.get("message")))
                .toList();
        List<List<String>> actual = warnings.stream()
                .map(w -> List.of(w.studentId(), w.code(), w.courseId(), w.prerequisiteCourseId(), w.message()))
                .toList();
        assertEquals(expected, actual);
    }

    @Test
    void solverFilesAreIdenticalToTheFeasibleDataset() throws Exception {
        assertEquals(new DatasetLoader().load(Path.of("data/feasible")), new DatasetLoader().load(DATASET));
    }

    @Test
    void prerequisiteInputsNeverChangeWhatTheSolverReceives() throws Exception {
        RecordingSolver solver = new RecordingSolver();
        SlotbookService service = new SlotbookService(new DatasetLoader(), solver);
        SlotbookRun withChecks = service.run(DATASET);
        SlotbookRun withoutChecks = service.run(DATASET, PrerequisiteInputs.none());

        assertEquals(solver.seen.get(0), solver.seen.get(1));
        assertTrue(withChecks.prerequisitesChecked());
        assertEquals(22, withChecks.prerequisiteWarnings().size());
        assertFalse(withoutChecks.prerequisitesChecked());
        assertTrue(withoutChecks.prerequisiteWarnings().isEmpty());
    }

    @Test
    void exporterWritesWarningsOnlyWhenPrerequisitesWereChecked(@TempDir Path output) throws Exception {
        SlotbookService service = new SlotbookService(new DatasetLoader(), new RecordingSolver());

        Path checked = output.resolve("checked");
        new SlotbookExporter().export(service.run(DATASET), checked);
        String csv = Files.readString(checked.resolve("prerequisite_warnings.csv"));
        assertTrue(csv.startsWith("student_id,code,course_id,prereq_course_id,message\n"));
        assertTrue(csv.contains("\"S002\",\"MISSING_PREREQUISITE\",\"EE5141\",\"EE3110\""));

        Path unchecked = output.resolve("unchecked");
        new SlotbookExporter().export(service.run(Path.of("data/feasible")), unchecked);
        assertFalse(Files.exists(unchecked.resolve("prerequisite_warnings.csv")));
    }

    @Test
    void theTwoArgumentRunConstructorKeepsTheV2Behaviour() {
        SlotbookRun run = new SlotbookRun(null, null);
        assertFalse(run.prerequisitesChecked());
        assertTrue(run.prerequisiteWarnings().isEmpty());
    }
}
