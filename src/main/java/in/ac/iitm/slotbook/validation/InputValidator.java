package in.ac.iitm.slotbook.validation;

import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.HardConflict;
import in.ac.iitm.slotbook.model.TimetableProblem;
import in.ac.iitm.slotbook.model.ValidationIssue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Performs deterministic checks before building a CP-SAT model. */
public final class InputValidator {

    public List<ValidationIssue> validate(TimetableProblem problem) {
        List<ValidationIssue> issues = new ArrayList<>();
        Map<String, Course> courses = problem.coursesById();
        Set<String> slotIds = problem.slotsById().keySet();

        Set<String> seenCourses = new HashSet<>();
        for (Course course : problem.courses()) {
            if (!seenCourses.add(course.id())) {
                issues.add(new ValidationIssue("DUPLICATE_COURSE", "Duplicate course ID: " + course.id()));
            }
            if (course.allowedSlots().isEmpty()) {
                issues.add(new ValidationIssue("NO_ALLOWED_SLOT", course.id() + " has no allowed slots"));
            }
            for (String slot : course.allowedSlots()) {
                if (!slotIds.contains(slot)) {
                    issues.add(new ValidationIssue("UNKNOWN_SLOT", course.id() + " references unknown slot " + slot));
                }
            }
            if (course.hasFixedSlot() && !course.allowedSlots().contains(course.fixedSlot())) {
                issues.add(new ValidationIssue("FIXED_NOT_ALLOWED",
                        course.id() + " is fixed to " + course.fixedSlot() + " but that slot is not allowed"));
            }
        }

        Set<String> seenSlots = new HashSet<>();
        problem.slots().forEach(slot -> {
            if (!seenSlots.add(slot.id())) {
                issues.add(new ValidationIssue("DUPLICATE_SLOT", "Duplicate slot ID: " + slot.id()));
            }
        });

        for (HardConflict conflict : problem.hardConflicts()) {
            if (!courses.containsKey(conflict.course1()) || !courses.containsKey(conflict.course2())) {
                issues.add(new ValidationIssue("UNKNOWN_CONFLICT_COURSE",
                        "Conflict references unknown course: " + conflict.course1() + ", " + conflict.course2()));
                continue;
            }
            Course first = courses.get(conflict.course1());
            Course second = courses.get(conflict.course2());
            if (first.hasFixedSlot() && second.hasFixedSlot() && first.fixedSlot().equals(second.fixedSlot())) {
                issues.add(new ValidationIssue("FIXED_CONFLICT",
                        first.id() + " and " + second.id() + " are both fixed to " + first.fixedSlot()
                                + " but cannot clash: " + conflict.reason()));
            }
        }

        problem.studentSelections().forEach(selection -> {
            if (!courses.containsKey(selection.courseId())) {
                issues.add(new ValidationIssue("UNKNOWN_STUDENT_COURSE",
                        selection.studentId() + " selected unknown course " + selection.courseId()));
            }
            if (selection.rank() < 1) {
                issues.add(new ValidationIssue("INVALID_RANK",
                        selection.studentId() + " supplied invalid rank " + selection.rank()));
            }
        });

        return List.copyOf(issues);
    }
}

