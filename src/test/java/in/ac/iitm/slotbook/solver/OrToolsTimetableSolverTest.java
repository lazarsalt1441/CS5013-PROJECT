package in.ac.iitm.slotbook.solver;

import in.ac.iitm.slotbook.io.DatasetLoader;
import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.HardConflict;
import in.ac.iitm.slotbook.model.SolveStatus;
import in.ac.iitm.slotbook.model.TimetableProblem;
import in.ac.iitm.slotbook.model.TimetableSolution;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrToolsTimetableSolverTest {
    private static TimetableProblem problem;
    private static TimetableSolution solution;

    @BeforeAll
    static void solveFixture() throws Exception {
        problem = new DatasetLoader().load(Path.of("data/feasible"));
        solution = new OrToolsTimetableSolver().solve(problem);
    }

    @Test
    void assignsEveryCourse() {
        assertTrue(solution.hasSolution());
        assertEquals(problem.courses().size(), solution.assignments().size());
    }

    @Test
    void usesOnlyAllowedSlotsAndHonoursFixedSlots() {
        for (Course course : problem.courses()) {
            String assigned = solution.assignments().get(course.id());
            assertNotNull(assigned);
            assertTrue(course.allowedSlots().contains(assigned));
            if (course.hasFixedSlot()) assertEquals(course.fixedSlot(), assigned);
        }
    }

    @Test
    void separatesEveryHardConflict() {
        for (HardConflict conflict : problem.hardConflicts()) {
            assertNotSame(
                    solution.assignments().get(conflict.course1()),
                    solution.assignments().get(conflict.course2()));
            assertTrue(!solution.assignments().get(conflict.course1())
                    .equals(solution.assignments().get(conflict.course2())));
        }
    }

    @Test
    void emitsOneCompleteExplanationPerCourse() {
        assertEquals(problem.courses().size(), solution.explanations().size());
        solution.explanations().values().forEach(explanation -> {
            assertFalseText(explanation.reasons().isEmpty());
            assertEquals(problem.slots().stream().filter(slot -> !slot.isLab()).count() - 1,
                    explanation.alternatives().size());
            assertTrue(explanation.asText().contains(explanation.assignedSlot()));
        });
    }

    @Test
    void rejectsKnownInfeasibleFixtureBeforeSolving() throws Exception {
        TimetableProblem invalid = new DatasetLoader().load(Path.of("data/infeasible-fixed-conflict"));
        TimetableSolution rejected = new OrToolsTimetableSolver().solve(invalid);
        assertEquals(SolveStatus.INVALID_INPUT, rejected.status());
        assertTrue(rejected.diagnostics().get(0).contains("FIXED_CONFLICT"));
    }

    private static void assertFalseText(boolean condition) {
        assertTrue(!condition);
    }
}

