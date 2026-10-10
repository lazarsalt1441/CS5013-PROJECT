package in.ac.iitm.slotbook.solver.unitime;

import in.ac.iitm.slotbook.io.DatasetLoader;
import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.SolveStatus;
import in.ac.iitm.slotbook.model.TimetableProblem;
import in.ac.iitm.slotbook.model.TimetableSolution;
import in.ac.iitm.slotbook.solver.OrToolsTimetableSolver;
import in.ac.iitm.slotbook.testing.ConstraintChecker;
import in.ac.iitm.slotbook.testing.PenaltyCalculator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniTimeTimetableSolverTest {
    /** Iteration cap keeps runs repeatable; the time limit is only a safety net. */
    static final UniTimeTimetableSolver SOLVER = new UniTimeTimetableSolver(10.0, 20_000);

    private static TimetableProblem problem;
    private static TimetableSolution solution;

    @BeforeAll
    static void solveFixture() throws Exception {
        problem = new DatasetLoader().load(Path.of("data/feasible"));
        solution = SOLVER.solve(problem);
    }

    @Test
    void assignsEveryCourseWithoutBreakingHardRules() {
        assertEquals(SolveStatus.FEASIBLE, solution.status(), solution.diagnostics().toString());
        assertEquals(problem.courses().size(), solution.assignments().size());
        assertEquals(List.of(), ConstraintChecker.violations(problem, solution.assignments()));
        for (Course course : problem.courses()) {
            String assigned = solution.assignments().get(course.id());
            assertNotNull(assigned);
            if (course.hasFixedSlot()) assertEquals(course.fixedSlot(), assigned);
        }
    }

    @Test
    void separatesTheRoomClashFromVersionOne() {
        // EE1100 is fixed to E and shares room CRC with EE1101; v1 put both in E.
        assertEquals("E", solution.assignments().get("EE1100"));
        assertNotEquals("E", solution.assignments().get("EE1101"));
    }

    @Test
    void uniTimeObjectiveEqualsTheSlotbookPenalty() {
        assertEquals(PenaltyCalculator.of(problem, solution.assignments()).total(), solution.objectiveValue(),
                () -> "UniTime criteria must reproduce the documented objective exactly: "
                        + PenaltyCalculator.of(problem, solution.assignments()) + " " + solution.diagnostics());
    }

    @Test
    void matchesTheExactOptimumOnTheFeasibleFixture() {
        long optimum = new OrToolsTimetableSolver().solve(problem).objectiveValue();
        assertEquals(optimum, solution.objectiveValue());
    }

    @Test
    void emitsOneCompleteExplanationPerCourse() {
        assertEquals(problem.courses().size(), solution.explanations().size());
        long lectureSlots = problem.slots().stream().filter(slot -> !slot.isLab()).count();
        solution.explanations().values().forEach(explanation -> {
            assertTrue(!explanation.reasons().isEmpty());
            assertEquals(lectureSlots - 1, explanation.alternatives().size());
        });
    }

    @Test
    void sameIterationCapGivesSameAnswer() {
        assertEquals(solution.assignments(), SOLVER.solve(problem).assignments());
    }

    @Test
    void rejectsKnownInfeasibleFixtureBeforeSolving() throws Exception {
        TimetableProblem invalid = new DatasetLoader().load(Path.of("data/infeasible-fixed-conflict"));
        TimetableSolution rejected = SOLVER.solve(invalid);
        assertEquals(SolveStatus.INVALID_INPUT, rejected.status());
        assertTrue(rejected.diagnostics().get(0).contains("FIXED_CONFLICT"));
    }
}
