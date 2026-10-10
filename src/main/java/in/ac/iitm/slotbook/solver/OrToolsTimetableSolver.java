package in.ac.iitm.slotbook.solver;

import com.google.ortools.Loader;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.LinearArgument;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import com.google.ortools.sat.Literal;
import in.ac.iitm.slotbook.explain.ExplanationBuilder;
import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.CoursePair;
import in.ac.iitm.slotbook.model.HardConflict;
import in.ac.iitm.slotbook.model.Slot;
import in.ac.iitm.slotbook.model.SolveStatus;
import in.ac.iitm.slotbook.model.TimeGrid;
import in.ac.iitm.slotbook.model.TimetableProblem;
import in.ac.iitm.slotbook.model.TimetableSolution;
import in.ac.iitm.slotbook.model.ValidationIssue;
import in.ac.iitm.slotbook.validation.InputValidator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** CP-SAT formulation of IITM course-to-slot allocation (v1 solver, kept as the exact reference). */
public final class OrToolsTimetableSolver implements TimetableSolver {
    private static final int STUDENT_CONFLICT_MULTIPLIER = Objective.STUDENT_CONFLICT_MULTIPLIER;

    public static final double DEFAULT_TIME_LIMIT_SECONDS = 10.0;

    static {
        Loader.loadNativeLibraries();
    }

    private final double timeLimitSeconds;

    public OrToolsTimetableSolver() {
        this(DEFAULT_TIME_LIMIT_SECONDS);
    }

    public OrToolsTimetableSolver(double timeLimitSeconds) {
        if (timeLimitSeconds <= 0) throw new IllegalArgumentException("Time limit must be positive");
        this.timeLimitSeconds = timeLimitSeconds;
    }

    @Override
    public TimetableSolution solve(TimetableProblem problem) {
        long startNanos = System.nanoTime();
        List<ValidationIssue> issues = new InputValidator().validate(problem);
        if (!issues.isEmpty()) {
            return new TimetableSolution(
                    SolveStatus.INVALID_INPUT,
                    Map.of(),
                    Map.of(),
                    issues.stream().map(issue -> issue.code() + ": " + issue.message()).toList(),
                    0,
                    elapsedSeconds(startNanos));
        }

        CpModel model = new CpModel();
        Map<String, Map<String, BoolVar>> variables = createAssignmentVariables(problem, model);
        addExactlyOneSlotPerCourse(problem, model, variables);
        addFixedAssignments(problem, model, variables);
        TimeGrid grid = problem.timeGrid();
        addOwnLabExclusions(problem, grid, model, variables);
        addHardConflicts(problem, grid, model, variables);
        addInstructorCollisionConstraints(problem, grid, model, variables);
        addRoomCollisionConstraints(problem, grid, model, variables);

        LinearExprBuilder objective = LinearExpr.newBuilder();
        addCourseSlotPenalties(problem, variables, objective);
        addStudentConflictPenalties(problem, grid, model, variables, objective);
        model.minimize(objective);

        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(timeLimitSeconds);
        solver.getParameters().setNumSearchWorkers(1);
        solver.getParameters().setRandomSeed(5013);
        CpSolverStatus solverStatus = solver.solve(model);
        SolveStatus status = translateStatus(solverStatus);

        if (status != SolveStatus.OPTIMAL && status != SolveStatus.FEASIBLE) {
            return new TimetableSolution(
                    status,
                    Map.of(),
                    Map.of(),
                    List.of("The CP-SAT model has no feasible assignment under the supplied hard constraints."),
                    0,
                    elapsedSeconds(startNanos));
        }

        Map<String, String> assignments = new LinkedHashMap<>();
        for (Course course : problem.courses()) {
            for (Slot slot : problem.slots()) {
                if (solver.booleanValue(variables.get(course.id()).get(slot.id()))) {
                    assignments.put(course.id(), slot.id());
                    break;
                }
            }
        }

        return new TimetableSolution(
                status,
                assignments,
                new ExplanationBuilder().buildAll(problem, assignments),
                List.of(
                        "CP-SAT status: " + solverStatus,
                        "Branches explored: " + solver.numBranches(),
                        "Conflicts encountered: " + solver.numConflicts()),
                Math.round(solver.objectiveValue()),
                elapsedSeconds(startNanos));
    }

    private Map<String, Map<String, BoolVar>> createAssignmentVariables(
            TimetableProblem problem, CpModel model) {
        Map<String, Map<String, BoolVar>> variables = new LinkedHashMap<>();
        for (Course course : problem.courses()) {
            Map<String, BoolVar> bySlot = new LinkedHashMap<>();
            for (Slot slot : problem.slots()) {
                BoolVar variable = model.newBoolVar("assign_" + course.id() + "_" + slot.id());
                bySlot.put(slot.id(), variable);
                if (!course.allowedSlots().contains(slot.id())) {
                    model.addEquality(variable, 0);
                }
            }
            variables.put(course.id(), bySlot);
        }
        return variables;
    }

    private void addExactlyOneSlotPerCourse(
            TimetableProblem problem, CpModel model, Map<String, Map<String, BoolVar>> variables) {
        for (Course course : problem.courses()) {
            model.addExactlyOne(variables.get(course.id()).values().toArray(Literal[]::new));
        }
    }

    private void addFixedAssignments(
            TimetableProblem problem, CpModel model, Map<String, Map<String, BoolVar>> variables) {
        for (Course course : problem.courses()) {
            if (course.hasFixedSlot()) {
                model.addEquality(variables.get(course.id()).get(course.fixedSlot()), 1);
            }
        }
    }

    private void addOwnLabExclusions(
            TimetableProblem problem, TimeGrid grid, CpModel model, Map<String, Map<String, BoolVar>> variables) {
        for (Course course : problem.courses()) {
            for (Slot slot : problem.slots()) {
                if (grid.overlapsOwnLab(course, slot.id())) {
                    model.addEquality(variables.get(course.id()).get(slot.id()), 0);
                }
            }
        }
    }

    private void addHardConflicts(
            TimetableProblem problem, TimeGrid grid, CpModel model, Map<String, Map<String, BoolVar>> variables) {
        Map<String, Course> courses = problem.coursesById();
        for (HardConflict conflict : problem.hardConflicts()) {
            forbidClashes(problem, grid, model, variables,
                    courses.get(conflict.course1()), courses.get(conflict.course2()), false);
        }
    }

    private void addInstructorCollisionConstraints(
            TimetableProblem problem, TimeGrid grid, CpModel model, Map<String, Map<String, BoolVar>> variables) {
        Map<String, List<Course>> byInstructor = new HashMap<>();
        for (Course course : problem.courses()) {
            byInstructor.computeIfAbsent(course.instructor(), ignored -> new ArrayList<>()).add(course);
        }
        forEachPair(byInstructor, (a, b) -> forbidClashes(problem, grid, model, variables, a, b, false));
    }

    private void addRoomCollisionConstraints(
            TimetableProblem problem, TimeGrid grid, CpModel model, Map<String, Map<String, BoolVar>> variables) {
        Map<String, List<Course>> byRoom = new HashMap<>();
        for (Course course : problem.courses()) {
            if (course.hasRoom()) {
                byRoom.computeIfAbsent(course.room().toUpperCase(), ignored -> new ArrayList<>()).add(course);
            }
        }
        forEachPair(byRoom, (a, b) -> forbidClashes(problem, grid, model, variables, a, b, true));
    }

    private static void forEachPair(Map<String, List<Course>> groups, java.util.function.BiConsumer<Course, Course> action) {
        for (List<Course> group : groups.values()) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    action.accept(group.get(i), group.get(j));
                }
            }
        }
    }

    /** Forbids every slot combination in which the two courses clash (lecture-only for rooms). */
    private void forbidClashes(
            TimetableProblem problem, TimeGrid grid, CpModel model, Map<String, Map<String, BoolVar>> variables,
            Course a, Course b, boolean roomOnly) {
        for (Slot slotA : problem.slots()) {
            if (!a.allowedSlots().contains(slotA.id())) continue;
            for (Slot slotB : problem.slots()) {
                if (!b.allowedSlots().contains(slotB.id())) continue;
                boolean clash = roomOnly
                        ? grid.roomClash(a, slotA.id(), b, slotB.id())
                        : grid.clash(a, slotA.id(), b, slotB.id());
                if (clash) {
                    model.addBoolOr(new Literal[] {
                            variables.get(a.id()).get(slotA.id()).not(),
                            variables.get(b.id()).get(slotB.id()).not()
                    });
                }
            }
        }
    }

    private void addCourseSlotPenalties(
            TimetableProblem problem,
            Map<String, Map<String, BoolVar>> variables,
            LinearExprBuilder objective) {
        Objective penalties = new Objective(problem);
        for (Course course : problem.courses()) {
            for (Slot slot : problem.slots()) {
                int penalty = penalties.coursePenalty(course, slot.id());
                if (penalty > 0) {
                    objective.addTerm(variables.get(course.id()).get(slot.id()), penalty);
                }
            }
        }
    }

    private void addStudentConflictPenalties(
            TimetableProblem problem,
            TimeGrid grid,
            CpModel model,
            Map<String, Map<String, BoolVar>> variables,
            LinearExprBuilder objective) {
        Map<String, Course> courses = problem.coursesById();
        for (Map.Entry<CoursePair, Integer> entry : problem.studentPairWeights().entrySet()) {
            CoursePair pair = entry.getKey();
            Course a = courses.get(pair.first());
            Course b = courses.get(pair.second());
            if (a == null || b == null) continue;
            for (Slot slotA : problem.slots()) {
                if (!a.allowedSlots().contains(slotA.id())) continue;
                for (Slot slotB : problem.slots()) {
                    if (!b.allowedSlots().contains(slotB.id())) continue;
                    int overlaps = grid.partOverlaps(a, slotA.id(), b, slotB.id());
                    if (overlaps == 0) continue;
                    BoolVar first = variables.get(a.id()).get(slotA.id());
                    BoolVar second = variables.get(b.id()).get(slotB.id());
                    BoolVar clash = model.newBoolVar(
                            "student_clash_" + a.id() + "_" + slotA.id() + "_" + b.id() + "_" + slotB.id());
                    model.addLessOrEqual(clash, first);
                    model.addLessOrEqual(clash, second);
                    model.addLessOrEqual(
                            LinearExpr.weightedSum(
                                    new LinearArgument[] {first, second, clash},
                                    new long[] {1, 1, -1}),
                            1);
                    objective.addTerm(clash, (long) entry.getValue() * overlaps * STUDENT_CONFLICT_MULTIPLIER);
                }
            }
        }
    }

    private SolveStatus translateStatus(CpSolverStatus status) {
        return switch (status) {
            case OPTIMAL -> SolveStatus.OPTIMAL;
            case FEASIBLE -> SolveStatus.FEASIBLE;
            case INFEASIBLE -> SolveStatus.INFEASIBLE;
            default -> SolveStatus.UNKNOWN;
        };
    }

    private double elapsedSeconds(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000_000.0;
    }
}

