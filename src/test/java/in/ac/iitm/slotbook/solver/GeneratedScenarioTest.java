package in.ac.iitm.slotbook.solver;

import in.ac.iitm.slotbook.io.DatasetLoader;
import in.ac.iitm.slotbook.model.TimetableProblem;
import in.ac.iitm.slotbook.model.TimetableSolution;
import in.ac.iitm.slotbook.testing.ConstraintChecker;
import in.ac.iitm.slotbook.testing.PenaltyCalculator;
import in.ac.iitm.slotbook.testing.PenaltyReport;
import in.ac.iitm.slotbook.testing.Scenario;
import in.ac.iitm.slotbook.testing.ScenarioCsvWriter;
import in.ac.iitm.slotbook.testing.ScenarioGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Solves every generated scenario and checks the result against independent oracles.
 * Writes target/penalty-report/penalty_report.csv and one dataset per scenario under
 * target/generated-scenarios/ so any case can be replayed through the CLI.
 */
class GeneratedScenarioTest {
    private static final long SEED = 5013;
    private static final int RANDOM_SCENARIOS = 6;
    private static final Path REPORT = Path.of("target/penalty-report/penalty_report.csv");
    private static final Path DATASETS = Path.of("target/generated-scenarios");

    private static final PenaltyReport report = new PenaltyReport();
    private static List<Scenario> scenarios;

    @BeforeAll
    static void generateScenarios() throws Exception {
        TimetableProblem base = new DatasetLoader().load(Path.of("data/feasible"));
        TimetableSolution baseline = new OrToolsTimetableSolver().solve(base);
        scenarios = new ScenarioGenerator(base, baseline.assignments()).catalogue(SEED, RANDOM_SCENARIOS);
    }

    @TestFactory
    Stream<DynamicTest> solverResultMatchesIndependentOracles() {
        return scenarios.stream().map(scenario -> DynamicTest.dynamicTest(
                scenario.name() + " (" + scenario.description() + ")", () -> {
                    ScenarioCsvWriter.write(scenario.problem(), DATASETS.resolve(scenario.name()));
                    TimetableSolution solution = new OrToolsTimetableSolver().solve(scenario.problem());
                    report.add(scenario, solution);

                    assertTrue(solution.hasSolution(),
                            "planted assignment proves feasibility, but status was " + solution.status()
                                    + " " + solution.diagnostics());
                    assertEquals(List.of(), ConstraintChecker.violations(scenario.problem(), solution.assignments()));
                    assertEquals(PenaltyCalculator.of(scenario.problem(), solution.assignments()).total(),
                            solution.objectiveValue(), "solver objective disagrees with recomputed penalty");
                    assertTrue(solution.objectiveValue()
                                    <= PenaltyCalculator.of(scenario.problem(), scenario.planted()).total(),
                            "solver did worse than the planted assignment");
                    assertEquals(scenario.problem().courses().size(), solution.explanations().size());
                }));
    }

    @Test
    void sameScenarioSolvesToSameObjective() {
        Scenario scenario = scenarios.get(scenarios.size() - 1);
        long first = new OrToolsTimetableSolver().solve(scenario.problem()).objectiveValue();
        long second = new OrToolsTimetableSolver().solve(scenario.problem()).objectiveValue();
        assertEquals(first, second);
    }

    @AfterAll
    static void publishReport() throws Exception {
        report.writeCsv(REPORT);
        System.out.println("\nPenalty report (" + REPORT + ")\n" + report.table());
    }
}
