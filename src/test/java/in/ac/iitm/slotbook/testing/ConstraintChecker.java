package in.ac.iitm.slotbook.testing;

import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.HardConflict;
import in.ac.iitm.slotbook.model.TimetableProblem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lists every hard-constraint violation in an assignment; an empty list means it is feasible. */
public final class ConstraintChecker {
    private ConstraintChecker() {}

    public static List<String> violations(TimetableProblem problem, Map<String, String> assignments) {
        List<String> violations = new ArrayList<>();
        Map<String, String> instructorSlots = new HashMap<>();
        for (Course course : problem.courses()) {
            String slot = assignments.get(course.id());
            if (slot == null) {
                violations.add(course.id() + " is unassigned");
                continue;
            }
            if (!course.allowedSlots().contains(slot)) {
                violations.add(course.id() + " uses disallowed slot " + slot);
            }
            if (course.hasFixedSlot() && !course.fixedSlot().equals(slot)) {
                violations.add(course.id() + " ignores fixed slot " + course.fixedSlot());
            }
            String clash = instructorSlots.put(course.instructor() + "\u0000" + slot, course.id());
            if (clash != null) {
                violations.add(course.instructor() + " teaches " + clash + " and " + course.id() + " in " + slot);
            }
        }
        for (HardConflict conflict : problem.hardConflicts()) {
            String first = assignments.get(conflict.course1());
            if (first != null && first.equals(assignments.get(conflict.course2()))) {
                violations.add(conflict.course1() + " and " + conflict.course2() + " share slot " + first);
            }
        }
        return violations;
    }
}
