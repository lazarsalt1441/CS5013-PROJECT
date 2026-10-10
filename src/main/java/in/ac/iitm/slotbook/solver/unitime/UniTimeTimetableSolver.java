package in.ac.iitm.slotbook.solver.unitime;

import in.ac.iitm.slotbook.explain.ExplanationBuilder;
import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.SolveStatus;
import in.ac.iitm.slotbook.model.TimetableProblem;
import in.ac.iitm.slotbook.model.TimetableSolution;
import in.ac.iitm.slotbook.model.ValidationIssue;
import in.ac.iitm.slotbook.solver.Objective;
import in.ac.iitm.slotbook.solver.TimetableSolver;
import in.ac.iitm.slotbook.validation.InputValidator;
import org.cpsolver.coursett.criteria.StudentOverlapConflict;
import org.cpsolver.coursett.criteria.TimePreferences;
import org.cpsolver.coursett.model.Lecture;
import org.cpsolver.coursett.model.Placement;
import org.cpsolver.ifs.assignment.Assignment;
import org.cpsolver.ifs.assignment.DefaultSingleAssignment;
import org.cpsolver.ifs.model.Constraint;
import org.cpsolver.ifs.solution.Solution;
import org.cpsolver.ifs.solver.Solver;
import org.cpsolver.ifs.util.DataProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Default v2 solver: delegates the search to UniTime's course timetabling engine (cpsolver).
 *
 * <p>The search is UniTime's standard pipeline: iterative forward search construction guided by
 * conflict-based statistics, then hill climbing, then great deluge until the time or iteration limit.
 * It does not prove optimality, so a complete answer is reported as {@link SolveStatus#FEASIBLE}.
 */
public final class UniTimeTimetableSolver implements TimetableSolver {
    public static final double DEFAULT_TIME_LIMIT_SECONDS = 10.0;
    public static final long SEED = 5013;
    private static final List<String> UNUSED_CRITERIA = List.of(
            "StudentConflict", "StudentDistanceConflict", "StudentHardConflict", "StudentCommittedConflict",
            "StudentWorkdayConflict", "UselessHalfHours", "BrokenTimePatterns", "TooBigRooms", "RoomPreferences",
            "DistributionPreferences", "SameSubpartBalancingPenalty", "DepartmentBalancingPenalty",
            "BackToBackInstructorPreferences", "Perturbations", "FlexibleConstraintCriterion");

    private final double timeLimitSeconds;
    private final long maxIterations;

    public UniTimeTimetableSolver() {
        this(DEFAULT_TIME_LIMIT_SECONDS, -1);
    }

    /**
     * @param timeLimitSeconds wall-clock limit for the search
     * @param maxIterations    iteration cap, or -1 for none; a cap makes runs repeatable for a fixed seed
     */
    public UniTimeTimetableSolver(double timeLimitSeconds, long maxIterations) {
        if (timeLimitSeconds <= 0) throw new IllegalArgumentException("Time limit must be positive");
        this.timeLimitSeconds = timeLimitSeconds;
        this.maxIterations = maxIterations;
    }

    @Override
    public TimetableSolution solve(TimetableProblem problem) {
        long startNanos = System.nanoTime();
        List<ValidationIssue> issues = new InputValidator().validate(problem);
        if (!issues.isEmpty()) {
            return new TimetableSolution(SolveStatus.INVALID_INPUT, Map.of(), Map.of(),
                    issues.stream().map(issue -> issue.code() + ": " + issue.message()).toList(),
                    0, elapsedSeconds(startNanos));
        }

        DataProperties properties = properties();
        Assignment<Lecture, Placement> assignment = new DefaultSingleAssignment<>();
        UniTimeModelBuilder.Built built = new UniTimeModelBuilder(problem).build(properties, assignment);

        Solver<Lecture, Placement> solver = new Solver<>(properties);
        solver.setInitalSolution(new Solution<>(built.model(), assignment));
        solver.start();
        try {
            solver.getSolverThread().join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            solver.stopSolver();
        }
        Solution<Lecture, Placement> solution = solver.currentSolution();
        if (solution.getBestInfo() != null) solution.restoreBest();
        Assignment<Lecture, Placement> best = solution.getAssignment();

        Map<String, String> assignments = new LinkedHashMap<>();
        List<String> unassigned = new ArrayList<>();
        for (Course course : problem.courses()) {
            Placement placement = best.getValue(built.lectures().get(course.id()));
            if (placement == null) unassigned.add(course.id());
            else assignments.put(course.id(), built.slotOf(placement));
        }

        // UniTime scores each placement relative to the cheapest time in that lecture's domain, so its
        // time-preference criterion omits a constant; adding it back gives the absolute slotbook penalty.
        long timePreferenceOffset = 0;
        for (Lecture lecture : built.lectures().values()) {
            timePreferenceOffset += Math.round(lecture.getMinMaxTimePreference()[0]);
        }
        long timePreference = Math.round(built.model().getCriterion(TimePreferences.class).getValue(best));
        long studentOverlaps = Math.round(built.model().getCriterion(StudentOverlapConflict.class).getValue(best));
        List<String> diagnostics = new ArrayList<>(List.of(
                "UniTime (cpsolver) search: construction + hill climbing + great deluge",
                "Iterations: " + solution.getIteration(),
                "Best solution found at iteration " + solution.getBestIteration()
                        + " after " + String.format("%.3f s", solution.getBestTime()),
                "UniTime time preference: " + timePreference
                        + " above the per-course minimum of " + timePreferenceOffset,
                "UniTime student overlap conflicts (weighted): " + studentOverlaps));

        if (!unassigned.isEmpty()) {
            diagnostics.add("Unassigned courses: " + String.join(", ", unassigned));
            for (String courseId : unassigned) {
                diagnostics.add(courseId + " is blocked by: " + blockingConstraints(built, best, courseId));
            }
            return new TimetableSolution(SolveStatus.INFEASIBLE, Map.of(), Map.of(), diagnostics,
                    0, elapsedSeconds(startNanos));
        }

        return new TimetableSolution(
                SolveStatus.FEASIBLE,
                assignments,
                new ExplanationBuilder().buildAll(problem, assignments),
                diagnostics,
                timePreferenceOffset + timePreference + Objective.STUDENT_CONFLICT_MULTIPLIER * studentOverlaps,
                elapsedSeconds(startNanos));
    }

    /** Names of the constraints that rule out each placement of an unassigned course. */
    private String blockingConstraints(
            UniTimeModelBuilder.Built built, Assignment<Lecture, Placement> assignment, String courseId) {
        Lecture lecture = built.lectures().get(courseId);
        Set<String> names = new TreeSet<>();
        for (Placement placement : lecture.values(assignment)) {
            Map<Constraint<Lecture, Placement>, Set<Placement>> conflicts =
                    built.model().conflictConstraints(assignment, placement);
            for (Map.Entry<Constraint<Lecture, Placement>, Set<Placement>> entry : conflicts.entrySet()) {
                StringBuilder name = new StringBuilder(entry.getKey().getClass().getSimpleName());
                if (entry.getKey().getName() != null) name.append(' ').append(entry.getKey().getName());
                entry.getValue().forEach(other -> name.append(" [").append(other.variable().getName()).append(']'));
                names.add(name.toString());
            }
        }
        return names.isEmpty() ? "no placements in its domain" : String.join("; ", names);
    }

    private DataProperties properties() {
        DataProperties properties = new DataProperties();
        properties.setProperty("General.Seed", Long.toString(SEED));
        // UniTime's default criteria stay registered (its constraints expect them); only time preference
        // and student overlaps carry weight, which makes UniTime's objective equal the slotbook objective.
        for (String unused : UNUSED_CRITERIA) properties.setProperty("Weight." + unused, "0");
        properties.setProperty("Weight.TimePreferences", "1");
        properties.setProperty("Weight.StudentOverlapConflict",
                Integer.toString(Objective.STUDENT_CONFLICT_MULTIPLIER));
        properties.setProperty("Termination.Class", "org.cpsolver.ifs.termination.GeneralTerminationCondition");
        properties.setProperty("Termination.StopWhenComplete", "false");
        properties.setProperty("Termination.TimeOut", Double.toString(timeLimitSeconds));
        properties.setProperty("Termination.MaxIters", Long.toString(maxIterations));
        properties.setProperty("Neighbour.Class", "org.cpsolver.ifs.algorithms.SimpleSearch");
        properties.setProperty("Search.GreatDeluge", "true");
        properties.setProperty("Value.Class", "org.cpsolver.coursett.heuristics.PlacementSelection");
        properties.setProperty("Variable.Class", "org.cpsolver.coursett.heuristics.LectureSelection");
        properties.setProperty("Extensions.Classes", "org.cpsolver.ifs.extension.ConflictStatistics");
        properties.setProperty("General.SaveBestUnassigned", "-1");
        return properties;
    }

    private double elapsedSeconds(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000_000.0;
    }
}
