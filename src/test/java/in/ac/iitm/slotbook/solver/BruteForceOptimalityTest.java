package in.ac.iitm.slotbook.solver;

import in.ac.iitm.slotbook.io.DatasetLoader;
import in.ac.iitm.slotbook.model.SolveStatus;
import in.ac.iitm.slotbook.model.TimetableProblem;
import in.ac.iitm.slotbook.model.TimetableSolution;
import in.ac.iitm.slotbook.testing.BruteForceSolver;
import in.ac.iitm.slotbook.testing.Scenario;
import in.ac.iitm.slotbook.testing.ScenarioGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Confirms CP-SAT reaches the true optimum on sub-problems small enough to enumerate. */
class BruteForceOptimalityTest {
    private static ScenarioGenerator generator;

    @BeforeAll
    static void createGenerator() throws Exception {
        TimetableProblem base = new DatasetLoader().load(Path.of("data/feasible"));
        generator = new ScenarioGenerator(base, new OrToolsTimetableSolver().solve(base).assignments());
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void cpSatMatchesExhaustiveOptimum(long seed) {
        Scenario scenario = generator.small("small-" + seed, new Random(seed), 6);
        OptionalLong expected = BruteForceSolver.optimum(scenario.problem());
        TimetableSolution solution = new OrToolsTimetableSolver().solve(scenario.problem());

        assertTrue(expected.isPresent(), "planted assignment guarantees a feasible answer");
        assertEquals(SolveStatus.OPTIMAL, solution.status());
        assertEquals(expected.getAsLong(), solution.objectiveValue());
    }
}
