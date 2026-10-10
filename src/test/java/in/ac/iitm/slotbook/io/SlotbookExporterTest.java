package in.ac.iitm.slotbook.io;

import in.ac.iitm.slotbook.service.SlotbookRun;
import in.ac.iitm.slotbook.service.SlotbookService;
import in.ac.iitm.slotbook.solver.SolverChoice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotbookExporterTest {
    @Test
    void exportsAssignmentsAndExplanations(@TempDir Path output) throws Exception {
        SlotbookRun run = new SlotbookService(SolverChoice.UNITIME, 1.0).run(Path.of("data/feasible"));
        new SlotbookExporter().export(run, output);
        Path assignments = output.resolve("generated_slotbook.csv");
        Path explanations = output.resolve("assignment_explanations.csv");
        assertTrue(Files.exists(assignments));
        assertTrue(Files.exists(explanations));
        assertTrue(Files.readString(assignments).contains("EE2002"));
        assertTrue(Files.readString(explanations).contains("rejected because"));
    }
}

