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
import in.ac.iitm.slotbook.model.InstructorPreference;
import in.ac.iitm.slotbook.model.Slot;
import in.ac.iitm.slotbook.model.SolveStatus;
import in.ac.iitm.slotbook.model.TimetableProblem;
import in.ac.iitm.slotbook.model.TimetableSolution;
import in.ac.iitm.slotbook.model.ValidationIssue;
import in.ac.iitm.slotbook.validation.InputValidator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** CP-SAT formulation of IITM course-to-slot allocation. */
public final class OrToolsTimetableSolver implements TimetableSolver {
    private static final int HISTORICAL_CHANGE_PENALTY = 2;
    private static final int STUDENT_CONFLICT_MULTIPLIER = 3;

    static {
        Loader.loadNativeLibraries();
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
        addHardConflicts(problem, model, variables);
        addInstructorCollisionConstraints(problem, model, variables);

        LinearExprBuilder objective = LinearExpr.newBuilder();
        addCourseSlotPenalties(problem, variables, objective);
        addStudentConflictPenalties(problem, model, variables, objective);
        model.minimize(objective);

        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(10.0);
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

        ExplanationBuilder explanationBuilder = new ExplanationBuilder(
                HISTORICAL_CHANGE_PENALTY, STUDENT_CONFLICT_MULTIPLIER);
        return new TimetableSolution(
                status,
                assignments,
                explanationBuilder.buildAll(problem, assignments),
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

    private void addHardConflicts(
            TimetableProblem problem, CpModel model, Map<String, Map<String, BoolVar>> variables) {
        for (HardConflict conflict : problem.hardConflicts()) {
            for (Slot slot : problem.slots()) {
                model.addAtMostOne(new BoolVar[] {
                        variables.get(conflict.course1()).get(slot.id()),
                        variables.get(conflict.course2()).get(slot.id())
                });
            }
        }
    }

    private void addInstructorCollisionConstraints(
            TimetableProblem problem, CpModel model, Map<String, Map<String, BoolVar>> variables) {
        Map<String, List<Course>> byInstructor = new HashMap<>();
        for (Course course : problem.courses()) {
            byInstructor.computeIfAbsent(course.instructor(), ignored -> new ArrayList<>()).add(course);
        }
        for (List<Course> courses : byInstructor.values()) {
            if (courses.size() < 2) continue;
            for (Slot slot : problem.slots()) {
                List<BoolVar> instructorVariables = courses.stream()
                        .map(course -> variables.get(course.id()).get(slot.id()))
                        .toList();
                model.addAtMostOne(instructorVariables.toArray(Literal[]::new));
            }
        }
    }

    private void addCourseSlotPenalties(
            TimetableProblem problem,
            Map<String, Map<String, BoolVar>> variables,
            LinearExprBuilder objective) {
        Map<String, Integer> preferencePenalties = new HashMap<>();
        for (InstructorPreference preference : problem.instructorPreferences()) {
            preferencePenalties.put(preference.instructor() + "\u0000" + preference.slotId(), preference.penalty());
        }
        for (Course course : problem.courses()) {
            for (Slot slot : problem.slots()) {
                int penalty = preferencePenalties.getOrDefault(course.instructor() + "\u0000" + slot.id(), 1);
                if (!course.historicalSlot().isBlank() && !course.historicalSlot().equals(slot.id())) {
                    penalty += HISTORICAL_CHANGE_PENALTY;
                }
                if (penalty > 0) {
                    objective.addTerm(variables.get(course.id()).get(slot.id()), penalty);
                }
            }
        }
    }

    private void addStudentConflictPenalties(
            TimetableProblem problem,
            CpModel model,
            Map<String, Map<String, BoolVar>> variables,
            LinearExprBuilder objective) {
        for (Map.Entry<CoursePair, Integer> entry : problem.studentPairWeights().entrySet()) {
            CoursePair pair = entry.getKey();
            if (!variables.containsKey(pair.first()) || !variables.containsKey(pair.second())) continue;
            for (Slot slot : problem.slots()) {
                BoolVar first = variables.get(pair.first()).get(slot.id());
                BoolVar second = variables.get(pair.second()).get(slot.id());
                BoolVar sameSlot = model.newBoolVar(
                        "student_clash_" + pair.first() + "_" + pair.second() + "_" + slot.id());
                model.addLessOrEqual(sameSlot, first);
                model.addLessOrEqual(sameSlot, second);
                model.addLessOrEqual(
                        LinearExpr.weightedSum(
                                new LinearArgument[] {first, second, sameSlot},
                                new long[] {1, 1, -1}),
                        1);
                objective.addTerm(sameSlot, (long) entry.getValue() * STUDENT_CONFLICT_MULTIPLIER);
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

