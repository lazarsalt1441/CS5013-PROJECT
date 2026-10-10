package in.ac.iitm.slotbook.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Complete validated input to the solver. */
public record TimetableProblem(
        List<Course> courses,
        List<Slot> slots,
        List<HardConflict> hardConflicts,
        List<InstructorPreference> instructorPreferences,
        List<StudentSelection> studentSelections) {

    public TimetableProblem {
        courses = List.copyOf(courses);
        slots = List.copyOf(slots);
        hardConflicts = List.copyOf(hardConflicts);
        instructorPreferences = List.copyOf(instructorPreferences);
        studentSelections = List.copyOf(studentSelections);
    }

    public Map<String, Course> coursesById() {
        Map<String, Course> result = new LinkedHashMap<>();
        courses.forEach(course -> result.put(course.id(), course));
        return result;
    }

    public Map<String, Slot> slotsById() {
        Map<String, Slot> result = new LinkedHashMap<>();
        slots.forEach(slot -> result.put(slot.id(), slot));
        return result;
    }

    /** Number of synthetic/anonymized students requesting each pair of courses. */
    public Map<CoursePair, Integer> studentPairWeights() {
        Map<String, List<StudentSelection>> byStudent = new HashMap<>();
        for (StudentSelection selection : studentSelections) {
            byStudent.computeIfAbsent(selection.studentId(), ignored -> new ArrayList<>()).add(selection);
        }

        Map<CoursePair, Integer> weights = new HashMap<>();
        for (List<StudentSelection> selections : byStudent.values()) {
            for (int i = 0; i < selections.size(); i++) {
                for (int j = i + 1; j < selections.size(); j++) {
                    StudentSelection a = selections.get(i);
                    StudentSelection b = selections.get(j);
                    if (!a.courseId().equals(b.courseId())) {
                        CoursePair pair = new CoursePair(a.courseId(), b.courseId());
                        int rankWeight = Math.max(1, 4 - Math.max(a.rank(), b.rank()));
                        weights.merge(pair, rankWeight, Integer::sum);
                    }
                }
            }
        }
        return Map.copyOf(weights);
    }
}

