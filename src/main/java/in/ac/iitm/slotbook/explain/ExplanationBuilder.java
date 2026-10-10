package in.ac.iitm.slotbook.explain;

import in.ac.iitm.slotbook.model.AssignmentExplanation;
import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.CoursePair;
import in.ac.iitm.slotbook.model.HardConflict;
import in.ac.iitm.slotbook.model.InstructorPreference;
import in.ac.iitm.slotbook.model.Slot;
import in.ac.iitm.slotbook.model.TimetableProblem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Converts solver evidence into auditable natural-language reasons without an LLM. */
public final class ExplanationBuilder {
    private final int historicalChangePenalty;
    private final int studentConflictMultiplier;

    public ExplanationBuilder(int historicalChangePenalty, int studentConflictMultiplier) {
        this.historicalChangePenalty = historicalChangePenalty;
        this.studentConflictMultiplier = studentConflictMultiplier;
    }

    public Map<String, AssignmentExplanation> buildAll(
            TimetableProblem problem, Map<String, String> assignments) {
        Map<String, AssignmentExplanation> result = new LinkedHashMap<>();
        for (Course course : problem.courses()) {
            result.put(course.id(), build(problem, course, assignments));
        }
        return result;
    }

    private AssignmentExplanation build(
            TimetableProblem problem, Course course, Map<String, String> assignments) {
        String assignedSlot = assignments.get(course.id());
        List<String> reasons = new ArrayList<>();
        List<String> alternatives = new ArrayList<>();

        if (course.hasFixedSlot()) {
            reasons.add("Administrative input fixed this course to slot " + assignedSlot + ".");
        } else {
            reasons.add("Slot " + assignedSlot + " satisfies every hard course and instructor constraint.");
        }

        Optional<InstructorPreference> preference = preference(problem, course, assignedSlot);
        if (preference.isPresent()) {
            reasons.add("Instructor preference penalty is " + preference.get().penalty()
                    + ": " + preference.get().reason() + ".");
        } else {
            reasons.add("No explicit instructor restriction applies; the default penalty is 1.");
        }

        if (assignedSlot.equals(course.historicalSlot())) {
            reasons.add("It preserves the historical slot and therefore adds no stability penalty.");
        } else if (!course.historicalSlot().isBlank()) {
            reasons.add("It moves from historical slot " + course.historicalSlot()
                    + " and incurs stability penalty " + historicalChangePenalty + ".");
        }

        int sameSlotDemand = studentConflictPenalty(problem, course.id(), assignedSlot, assignments);
        reasons.add("Its weighted student co-demand conflict contribution in this solution is "
                + sameSlotDemand + ".");

        for (Slot slot : problem.slots()) {
            if (slot.id().equals(assignedSlot)) continue;
            alternatives.add(describeAlternative(problem, course, slot.id(), assignments));
        }

        return new AssignmentExplanation(course.id(), assignedSlot, reasons, alternatives);
    }

    private String describeAlternative(
            TimetableProblem problem, Course course, String slotId, Map<String, String> assignments) {
        if (!course.allowedSlots().contains(slotId)) {
            return slotId + ": rejected because it is outside the course's allowed-slot set.";
        }
        if (course.hasFixedSlot() && !course.fixedSlot().equals(slotId)) {
            return slotId + ": rejected because the course is administratively fixed to "
                    + course.fixedSlot() + ".";
        }

        for (HardConflict conflict : problem.hardConflicts()) {
            if (conflict.contains(course.id())) {
                String other = conflict.other(course.id());
                if (slotId.equals(assignments.get(other))) {
                    return slotId + ": rejected because it would clash with " + other
                            + " (" + conflict.reason() + ").";
                }
            }
        }

        for (Course other : problem.courses()) {
            if (!other.id().equals(course.id())
                    && other.instructor().equals(course.instructor())
                    && slotId.equals(assignments.get(other.id()))) {
                return slotId + ": rejected because instructor " + course.instructor()
                        + " is already assigned to " + other.id() + ".";
            }
        }

        int basePenalty = preference(problem, course, slotId).map(InstructorPreference::penalty).orElse(1);
        if (!course.historicalSlot().isBlank() && !course.historicalSlot().equals(slotId)) {
            basePenalty += historicalChangePenalty;
        }
        int studentPenalty = studentConflictPenalty(problem, course.id(), slotId, assignments);
        return slotId + ": feasible alternative; estimated local penalty = " + basePenalty
                + " course/instructor + " + studentPenalty + " student-conflict.";
    }

    private Optional<InstructorPreference> preference(
            TimetableProblem problem, Course course, String slotId) {
        return problem.instructorPreferences().stream()
                .filter(item -> item.instructor().equals(course.instructor()) && item.slotId().equals(slotId))
                .findFirst();
    }

    private int studentConflictPenalty(
            TimetableProblem problem,
            String courseId,
            String candidateSlot,
            Map<String, String> assignments) {
        int result = 0;
        for (Map.Entry<CoursePair, Integer> entry : problem.studentPairWeights().entrySet()) {
            CoursePair pair = entry.getKey();
            String other = null;
            if (pair.first().equals(courseId)) other = pair.second();
            if (pair.second().equals(courseId)) other = pair.first();
            if (other != null && candidateSlot.equals(assignments.get(other))) {
                result += entry.getValue() * studentConflictMultiplier;
            }
        }
        return result;
    }
}

