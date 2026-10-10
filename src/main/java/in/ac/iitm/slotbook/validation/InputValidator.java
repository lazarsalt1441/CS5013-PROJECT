package in.ac.iitm.slotbook.validation;

import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.HardConflict;
import in.ac.iitm.slotbook.model.InstructorPreference;
import in.ac.iitm.slotbook.model.Slot;
import in.ac.iitm.slotbook.model.TimeGrid;
import in.ac.iitm.slotbook.model.TimetableProblem;
import in.ac.iitm.slotbook.model.ValidationIssue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Performs deterministic checks before handing the problem to any solver. */
public final class InputValidator {

    public List<ValidationIssue> validate(TimetableProblem problem) {
        List<ValidationIssue> issues = new ArrayList<>();
        Map<String, Course> courses = problem.coursesById();
        Map<String, Slot> slots = problem.slotsById();

        Set<String> seenSlots = new HashSet<>();
        for (Slot slot : problem.slots()) {
            if (!seenSlots.add(slot.id())) {
                issues.add(new ValidationIssue("DUPLICATE_SLOT", "Duplicate slot ID: " + slot.id()));
            }
            try {
                slot.meetings();
            } catch (RuntimeException error) {
                issues.add(new ValidationIssue("INVALID_SLOT_TIMES", error.getMessage()));
            }
        }
        if (issues.stream().anyMatch(issue -> issue.code().equals("INVALID_SLOT_TIMES"))) {
            return List.copyOf(issues);
        }
        TimeGrid grid = problem.timeGrid();

        Set<String> seenCourses = new HashSet<>();
        for (Course course : problem.courses()) {
            if (!seenCourses.add(course.id())) {
                issues.add(new ValidationIssue("DUPLICATE_COURSE", "Duplicate course ID: " + course.id()));
            }
            if (course.allowedSlots().isEmpty()) {
                issues.add(new ValidationIssue("NO_ALLOWED_SLOT", course.id() + " has no allowed slots"));
            }
            for (String slot : course.allowedSlots()) {
                if (!slots.containsKey(slot)) {
                    issues.add(new ValidationIssue("UNKNOWN_SLOT", course.id() + " references unknown slot " + slot));
                } else if (slots.get(slot).isLab()) {
                    issues.add(new ValidationIssue("LAB_AS_LECTURE",
                            course.id() + " allows lab slot " + slot + " as its lecture slot"));
                }
            }
            if (course.hasFixedSlot() && !course.allowedSlots().contains(course.fixedSlot())) {
                issues.add(new ValidationIssue("FIXED_NOT_ALLOWED",
                        course.id() + " is fixed to " + course.fixedSlot() + " but that slot is not allowed"));
            }
            if (!course.historicalSlot().isBlank() && !slots.containsKey(course.historicalSlot())) {
                issues.add(new ValidationIssue("UNKNOWN_HISTORICAL_SLOT",
                        course.id() + " has unknown historical slot " + course.historicalSlot()));
            }
            if (course.hasLab()) {
                Slot lab = slots.get(course.additionalSlot());
                if (lab == null || !lab.isLab()) {
                    issues.add(new ValidationIssue("UNKNOWN_LAB_SLOT",
                            course.id() + " has additional slot " + course.additionalSlot() + " that is not a LAB slot"));
                } else if (course.hasFixedSlot() && grid.overlapsOwnLab(course, course.fixedSlot())) {
                    issues.add(new ValidationIssue("FIXED_OVERLAPS_LAB",
                            course.id() + " is fixed to " + course.fixedSlot() + ", which overlaps its own lab"));
                }
            }
        }

        Set<String> seenPreferences = new HashSet<>();
        for (InstructorPreference preference : problem.instructorPreferences()) {
            if (!seenPreferences.add(preference.instructor() + "\u0000" + preference.slotId())) {
                issues.add(new ValidationIssue("DUPLICATE_PREFERENCE",
                        preference.instructor() + " has more than one preference for slot " + preference.slotId()));
            }
            if (preference.penalty() < 0) {
                issues.add(new ValidationIssue("NEGATIVE_PENALTY",
                        preference.instructor() + " has negative penalty " + preference.penalty()
                                + " for slot " + preference.slotId()));
            }
        }

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
            if (first.hasLab() && second.hasLab() && grid.overlaps(first.additionalSlot(), second.additionalSlot())) {
                issues.add(new ValidationIssue("LAB_CONFLICT",
                        first.id() + " and " + second.id() + " have overlapping labs but cannot clash: "
                                + conflict.reason()));
            }
        }

        List<Course> withLabs = problem.courses().stream().filter(Course::hasLab).toList();
        for (int i = 0; i < withLabs.size(); i++) {
            for (int j = i + 1; j < withLabs.size(); j++) {
                Course a = withLabs.get(i);
                Course b = withLabs.get(j);
                if (a.instructor().equals(b.instructor()) && grid.overlaps(a.additionalSlot(), b.additionalSlot())) {
                    issues.add(new ValidationIssue("INSTRUCTOR_LAB_CONFLICT",
                            a.instructor() + " teaches overlapping labs for " + a.id() + " and " + b.id()));
                }
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
