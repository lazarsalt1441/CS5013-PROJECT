package in.ac.iitm.slotbook.explain;

import in.ac.iitm.slotbook.model.AssignmentExplanation;
import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.CoursePair;
import in.ac.iitm.slotbook.model.HardConflict;
import in.ac.iitm.slotbook.model.InstructorPreference;
import in.ac.iitm.slotbook.model.Slot;
import in.ac.iitm.slotbook.model.TimeGrid;
import in.ac.iitm.slotbook.model.TimetableProblem;
import in.ac.iitm.slotbook.solver.Objective;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Converts a solved assignment into auditable natural-language reasons without an LLM.
 * It reads only the problem and the final assignment, so it explains the output of any solver.
 */
public final class ExplanationBuilder {

    public Map<String, AssignmentExplanation> buildAll(
            TimetableProblem problem, Map<String, String> assignments) {
        Context context = new Context(problem, assignments);
        Map<String, AssignmentExplanation> result = new LinkedHashMap<>();
        for (Course course : problem.courses()) {
            result.put(course.id(), build(context, course));
        }
        return result;
    }

    private record Context(
            TimetableProblem problem,
            Map<String, String> assignments,
            TimeGrid grid,
            Objective objective,
            Map<String, Course> courses,
            Map<CoursePair, Integer> pairWeights) {
        Context(TimetableProblem problem, Map<String, String> assignments) {
            this(problem, assignments, problem.timeGrid(), new Objective(problem),
                    problem.coursesById(), problem.studentPairWeights());
        }
    }

    private AssignmentExplanation build(Context context, Course course) {
        String assignedSlot = context.assignments().get(course.id());
        List<String> reasons = new ArrayList<>();
        List<String> alternatives = new ArrayList<>();

        if (course.hasFixedSlot()) {
            reasons.add("Administrative input fixed this course to slot " + assignedSlot + ".");
        } else {
            reasons.add("Slot " + assignedSlot + " satisfies every hard course, instructor and room constraint.");
        }
        if (course.hasLab()) {
            reasons.add("Its lab in slot " + course.additionalSlot()
                    + " is fixed and does not overlap the lecture slot.");
        }

        Optional<InstructorPreference> preference = preference(context.problem(), course, assignedSlot);
        if (preference.isPresent()) {
            reasons.add("Instructor preference penalty is " + preference.get().penalty()
                    + ": " + preference.get().reason() + ".");
        } else {
            reasons.add("No explicit instructor restriction applies; the default penalty is "
                    + Objective.DEFAULT_INSTRUCTOR_PENALTY + ".");
        }

        if (assignedSlot.equals(course.historicalSlot())) {
            reasons.add("It preserves the historical slot and therefore adds no stability penalty.");
        } else if (!course.historicalSlot().isBlank()) {
            reasons.add("It moves from historical slot " + course.historicalSlot()
                    + " and incurs stability penalty " + Objective.HISTORICAL_CHANGE_PENALTY + ".");
        }

        reasons.add("Its weighted student co-demand conflict contribution in this solution is "
                + studentConflictPenalty(context, course, assignedSlot) + ".");

        for (Slot slot : context.problem().slots()) {
            if (slot.isLab() || slot.id().equals(assignedSlot)) continue;
            alternatives.add(describeAlternative(context, course, slot.id()));
        }

        return new AssignmentExplanation(course.id(), assignedSlot, reasons, alternatives);
    }

    private String describeAlternative(Context context, Course course, String slotId) {
        if (!course.allowedSlots().contains(slotId)) {
            return slotId + ": rejected because it is outside the course's allowed-slot set.";
        }
        if (course.hasFixedSlot() && !course.fixedSlot().equals(slotId)) {
            return slotId + ": rejected because the course is administratively fixed to "
                    + course.fixedSlot() + ".";
        }
        if (context.grid().overlapsOwnLab(course, slotId)) {
            return slotId + ": rejected because it overlaps the course's own lab " + course.additionalSlot() + ".";
        }

        for (HardConflict conflict : context.problem().hardConflicts()) {
            if (conflict.contains(course.id())) {
                Course other = context.courses().get(conflict.other(course.id()));
                String otherSlot = context.assignments().get(other.id());
                if (context.grid().clash(course, slotId, other, otherSlot)) {
                    return slotId + ": rejected because it would clash with " + other.id()
                            + " (" + conflict.reason() + ").";
                }
            }
        }

        for (Course other : context.problem().courses()) {
            if (other.id().equals(course.id())) continue;
            String otherSlot = context.assignments().get(other.id());
            if (other.instructor().equals(course.instructor())
                    && context.grid().clash(course, slotId, other, otherSlot)) {
                return slotId + ": rejected because instructor " + course.instructor()
                        + " already teaches " + other.id() + " at an overlapping time.";
            }
            if (context.grid().roomClash(course, slotId, other, otherSlot)) {
                return slotId + ": rejected because room " + course.room()
                        + " is already used by " + other.id() + " at an overlapping time.";
            }
        }

        int basePenalty = context.objective().coursePenalty(course, slotId);
        int studentPenalty = studentConflictPenalty(context, course, slotId);
        return slotId + ": feasible alternative; estimated local penalty = " + basePenalty
                + " course/instructor + " + studentPenalty + " student-conflict.";
    }

    private Optional<InstructorPreference> preference(TimetableProblem problem, Course course, String slotId) {
        return problem.instructorPreferences().stream()
                .filter(item -> item.instructor().equals(course.instructor()) && item.slotId().equals(slotId))
                .findFirst();
    }

    private int studentConflictPenalty(Context context, Course course, String candidateSlot) {
        int result = 0;
        for (Map.Entry<CoursePair, Integer> entry : context.pairWeights().entrySet()) {
            CoursePair pair = entry.getKey();
            String otherId = null;
            if (pair.first().equals(course.id())) otherId = pair.second();
            if (pair.second().equals(course.id())) otherId = pair.first();
            if (otherId == null) continue;
            Course other = context.courses().get(otherId);
            int overlaps = context.grid().partOverlaps(course, candidateSlot, other, context.assignments().get(otherId));
            result += entry.getValue() * overlaps * Objective.STUDENT_CONFLICT_MULTIPLIER;
        }
        return result;
    }
}
