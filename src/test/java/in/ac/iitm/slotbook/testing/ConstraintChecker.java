package in.ac.iitm.slotbook.testing;

import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.HardConflict;
import in.ac.iitm.slotbook.model.TimeGrid;
import in.ac.iitm.slotbook.model.TimetableProblem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Lists every hard-constraint violation in an assignment; an empty list means it is feasible. */
public final class ConstraintChecker {
    private ConstraintChecker() {}

    public static List<String> violations(TimetableProblem problem, Map<String, String> assignments) {
        List<String> violations = new ArrayList<>();
        TimeGrid grid = problem.timeGrid();
        Map<String, Course> courses = problem.coursesById();
        List<Course> placed = new ArrayList<>();
        for (Course course : problem.courses()) {
            String slot = assignments.get(course.id());
            if (slot == null) {
                violations.add(course.id() + " is unassigned");
                continue;
            }
            placed.add(course);
            if (!course.allowedSlots().contains(slot)) {
                violations.add(course.id() + " uses disallowed slot " + slot);
            }
            if (course.hasFixedSlot() && !course.fixedSlot().equals(slot)) {
                violations.add(course.id() + " ignores fixed slot " + course.fixedSlot());
            }
            if (grid.overlapsOwnLab(course, slot)) {
                violations.add(course.id() + " lecture " + slot + " overlaps its own lab " + course.additionalSlot());
            }
        }
        for (int i = 0; i < placed.size(); i++) {
            for (int j = i + 1; j < placed.size(); j++) {
                Course a = placed.get(i);
                Course b = placed.get(j);
                String slotA = assignments.get(a.id());
                String slotB = assignments.get(b.id());
                if (a.instructor().equals(b.instructor()) && grid.clash(a, slotA, b, slotB)) {
                    violations.add(a.instructor() + " teaches " + a.id() + " and " + b.id() + " at overlapping times");
                }
                if (grid.roomClash(a, slotA, b, slotB)) {
                    violations.add(a.id() + " and " + b.id() + " share room " + a.room() + " at overlapping times");
                }
            }
        }
        for (HardConflict conflict : problem.hardConflicts()) {
            Course a = courses.get(conflict.course1());
            Course b = courses.get(conflict.course2());
            String slotA = assignments.get(a.id());
            String slotB = assignments.get(b.id());
            if (slotA != null && slotB != null && grid.clash(a, slotA, b, slotB)) {
                violations.add(a.id() + " and " + b.id() + " overlap (" + slotA + ", " + slotB + ")");
            }
        }
        return violations;
    }
}
