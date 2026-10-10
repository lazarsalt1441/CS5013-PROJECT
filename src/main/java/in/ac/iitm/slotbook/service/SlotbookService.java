package in.ac.iitm.slotbook.service;

import in.ac.iitm.slotbook.io.DatasetLoader;
import in.ac.iitm.slotbook.model.TimetableProblem;
import in.ac.iitm.slotbook.model.TimetableSolution;
import in.ac.iitm.slotbook.solver.OrToolsTimetableSolver;
import in.ac.iitm.slotbook.solver.TimetableSolver;

import java.io.IOException;
import java.nio.file.Path;

/** Application facade shared by the CLI and JavaFX interface. */
public final class SlotbookService {
    private final DatasetLoader loader;
    private final TimetableSolver solver;

    public SlotbookService() {
        this(new DatasetLoader(), new OrToolsTimetableSolver());
    }

    public SlotbookService(DatasetLoader loader, TimetableSolver solver) {
        this.loader = loader;
        this.solver = solver;
    }

    public SlotbookRun run(Path datasetDirectory) throws IOException {
        TimetableProblem problem = loader.load(datasetDirectory);
        TimetableSolution solution = solver.solve(problem);
        return new SlotbookRun(problem, solution);
    }
}

