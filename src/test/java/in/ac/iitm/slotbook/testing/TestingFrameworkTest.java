package in.ac.iitm.slotbook.testing;

import in.ac.iitm.slotbook.io.DatasetLoader;
import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.HardConflict;
import in.ac.iitm.slotbook.model.InstructorPreference;
import in.ac.iitm.slotbook.model.Slot;
import in.ac.iitm.slotbook.model.StudentSelection;
import in.ac.iitm.slotbook.model.TimetableProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the oracles themselves on hand-computed cases, so a passing scenario test means something. */
class TestingFrameworkTest {
    private static final TimetableProblem TINY = new TimetableProblem(
            List.of(
                    course("C1", "Alice", "A", "", Set.of("A", "B")),
                    course("C2", "Bob", "B", "", Set.of("A", "B")),
                    course("C3", "Alice", "", "B", Set.of("A", "B"))),
            List.of(new Slot("A", "MON", "08:00", "LECTURE"), new Slot("B", "TUE", "09:00", "LECTURE")),
            List.of(new HardConflict("C1", "C2", "test")),
            List.of(new InstructorPreference("Alice", "A", 5, "test"), new InstructorPreference("Bob", "A", 0, "test")),
            List.of(new StudentSelection("S1", "C2", 1), new StudentSelection("S1", "C3", 2)));

    @Test
    void penaltyCalculatorMatchesHandComputation() {
        // C1->B: default 1 + moved from A (2). C2->A: Bob/A = 0 + moved from B (2). C3->B: default 1.
        // No student clash: C2 (A) and C3 (B) differ.
        PenaltyCalculator.Breakdown breakdown = PenaltyCalculator.of(TINY, Map.of("C1", "B", "C2", "A", "C3", "B"));
        assertEquals(new PenaltyCalculator.Breakdown(2, 4, 0), breakdown);
        assertEquals(6, breakdown.total());

        // C2 and C3 both in B: pair weight max(1, 4 - 2) = 2, times multiplier 3.
        assertEquals(6, PenaltyCalculator.of(TINY, Map.of("C1", "A", "C2", "B", "C3", "B")).student());
    }

    @Test
    void constraintCheckerFlagsEveryViolationKind() {
        assertEquals(List.of(), ConstraintChecker.violations(TINY, Map.of("C1", "A", "C2", "B", "C3", "B")));
        List<String> violations = ConstraintChecker.violations(TINY, Map.of("C1", "A", "C2", "A", "C3", "A"));
        assertTrue(violations.stream().anyMatch(v -> v.contains("fixed slot")), violations::toString);
        assertTrue(violations.stream().anyMatch(v -> v.contains("Alice teaches")), violations::toString);
        assertTrue(violations.stream().anyMatch(v -> v.contains("C1 and C2 overlap")), violations::toString);
        assertTrue(ConstraintChecker.violations(TINY, Map.of("C1", "A")).stream()
                .anyMatch(v -> v.contains("unassigned")));
    }

    /** A lecture slot X overlapping lab slot L, a disjoint slot Y, and two courses sharing room R1. */
    private static final TimetableProblem TIMED = new TimetableProblem(
            List.of(
                    new Course("L1", "Lab course", "G1", "CORE", "Carol", 10, "", "", Set.of("X", "Y"), "L", "R1"),
                    new Course("R2", "Room mate", "G1", "CORE", "Dan", 10, "", "", Set.of("X", "Y"), "", "R1")),
            List.of(
                    new Slot("X", "MON|TUE", "14:30|08:00", "50|50", "LECTURE"),
                    new Slot("Y", "WED", "09:00", "", "LECTURE"),
                    new Slot("L", "MON", "14:00", "165", "LAB")),
            List.of(),
            List.of(),
            List.of(new StudentSelection("S1", "L1", 1), new StudentSelection("S1", "R2", 1)));

    @Test
    void timeGridUsesRealMinutes() {
        in.ac.iitm.slotbook.model.TimeGrid grid = TIMED.timeGrid();
        assertTrue(grid.overlaps("X", "L"), "Mon 14:30-15:20 lies inside the 14:00-16:45 lab");
        assertTrue(!grid.overlaps("Y", "L") && !grid.overlaps("X", "Y"));
        // Back-to-back meetings do not overlap: 08:00-08:50 then 08:50.
        assertTrue(!new Slot("P1", "MON", "08:00", "50", "LECTURE")
                .overlaps(new Slot("P2", "MON", "08:50", "50", "LECTURE")));
    }

    @Test
    void checkerFlagsOwnLabAndRoomClashes() {
        List<String> violations = ConstraintChecker.violations(TIMED, Map.of("L1", "X", "R2", "X"));
        assertTrue(violations.stream().anyMatch(v -> v.contains("overlaps its own lab")), violations::toString);
        assertTrue(violations.stream().anyMatch(v -> v.contains("share room R1")), violations::toString);
        assertEquals(List.of(), ConstraintChecker.violations(TIMED, Map.of("L1", "Y", "R2", "X")));
    }

    @Test
    void studentPenaltyCountsLabOverlaps() {
        // L1 lecture in Y, its lab L on Monday afternoon; R2 lecture in X overlaps that lab once.
        // Pair weight max(1, 4 - 1) = 3, times one overlapping part pair, times multiplier 3.
        assertEquals(9, PenaltyCalculator.of(TIMED, Map.of("L1", "Y", "R2", "X")).student());
        // Both lectures in Y: lecture/lecture overlap only (R2 has no lab).
        assertEquals(9, PenaltyCalculator.of(TIMED, Map.of("L1", "Y", "R2", "Y")).student());
    }

    @Test
    void bruteForceFindsHandComputedOptimum() {
        // C3 fixed to B, so C1 (Alice) must be A, so C2 must be B.
        // Instructor 5 + 1 + 1 = 7, no moves, C2/C3 share B so student clash = 6.
        assertEquals(13,BruteForceSolver.optimum(TINY).orElseThrow());
    }

    @Test
    void generatedScenariosKeepPlantedAssignmentFeasible() throws Exception {
        TimetableProblem base = new DatasetLoader().load(Path.of("data/feasible"));
        Map<String, String> planted = new in.ac.iitm.slotbook.solver.OrToolsTimetableSolver().solve(base).assignments();
        for (Scenario scenario : new ScenarioGenerator(base, planted).catalogue(99, 10)) {
            assertEquals(List.of(), ConstraintChecker.violations(scenario.problem(), scenario.planted()),
                    scenario.name());
        }
    }

    @Test
    void csvWriterRoundTripsThroughDatasetLoader(@TempDir Path directory) throws Exception {
        ScenarioCsvWriter.write(TINY, directory);
        TimetableProblem reloaded = new DatasetLoader().load(directory);
        assertEquals(TINY.courses(), reloaded.courses());
        assertEquals(TINY.hardConflicts(), reloaded.hardConflicts());
        assertEquals(TINY.instructorPreferences(), reloaded.instructorPreferences());
        assertEquals(TINY.studentSelections(), reloaded.studentSelections());
    }

    private static Course course(String id, String instructor, String historical, String fixed, Set<String> allowed) {
        return new Course(id, id + " title", "G1", "CORE", instructor, 10, historical, fixed, allowed, "", "");
    }
}
