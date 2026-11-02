package in.ac.iitm.slotbook.service;

import in.ac.iitm.slotbook.io.DatasetLoader;
import in.ac.iitm.slotbook.io.PrerequisiteInputs;
import in.ac.iitm.slotbook.io.PrerequisiteLoader;
import in.ac.iitm.slotbook.model.PrerequisiteData;
import in.ac.iitm.slotbook.model.PrerequisiteWarning;
import in.ac.iitm.slotbook.model.TimetableProblem;
import in.ac.iitm.slotbook.model.TimetableSolution;
import in.ac.iitm.slotbook.solver.SolverChoice;
import in.ac.iitm.slotbook.solver.TimetableSolver;
import in.ac.iitm.slotbook.validation.PrerequisiteChecker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Application facade shared by the CLI and JavaFX interface. */
public final class SlotbookService {
    public static final double DEFAULT_TIME_LIMIT_SECONDS = 10.0;

    private final DatasetLoader loader;
    private final TimetableSolver solver;
    private final PrerequisiteLoader prerequisiteLoader = new PrerequisiteLoader();
    private final PrerequisiteChecker prerequisiteChecker = new PrerequisiteChecker();

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

    /** Solves the dataset, checking prerequisites if the optional files are in the same directory. */
    public SlotbookRun run(Path datasetDirectory) throws IOException {
        return run(datasetDirectory, PrerequisiteInputs.fromDirectory(datasetDirectory));
    }

    /**
     * Solves the dataset and reports prerequisite warnings from {@code inputs}. The warnings are
     * computed from the same student selections but are not passed to the solver.
     */
    public SlotbookRun run(Path datasetDirectory, PrerequisiteInputs inputs) throws IOException {
        TimetableProblem problem = loader.load(datasetDirectory);
        PrerequisiteData prerequisites = prerequisiteLoader.load(inputs);
        List<PrerequisiteWarning> warnings = prerequisiteChecker.check(problem.studentSelections(), prerequisites);
        TimetableSolution solution = solver.solve(problem);
        return new SlotbookRun(problem, solution, warnings, !prerequisites.hasNoRules());
    }
}
