package in.ac.iitm.slotbook.service;

import in.ac.iitm.slotbook.io.DatasetLoader;
import in.ac.iitm.slotbook.model.TimetableProblem;
import in.ac.iitm.slotbook.model.TimetableSolution;
import in.ac.iitm.slotbook.solver.SolverChoice;
import in.ac.iitm.slotbook.solver.TimetableSolver;

import java.io.IOException;
import java.nio.file.Path;

/** Application facade shared by the CLI and JavaFX interface. */
public final class SlotbookService {
    public static final double DEFAULT_TIME_LIMIT_SECONDS = 10.0;

    private final DatasetLoader loader;
    private final TimetableSolver solver;

    /** Uses the default UniTime solver. */
    public SlotbookService() {
        this(SolverChoice.UNITIME, DEFAULT_TIME_LIMIT_SECONDS);
    }

    public SlotbookService(SolverChoice choice, double timeLimitSeconds) {
        this(new DatasetLoader(), choice.create(timeLimitSeconds));
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
