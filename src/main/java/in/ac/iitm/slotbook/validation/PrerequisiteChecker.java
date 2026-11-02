package in.ac.iitm.slotbook.validation;

import in.ac.iitm.slotbook.model.CompletedCourse;
import in.ac.iitm.slotbook.model.Prerequisite;
import in.ac.iitm.slotbook.model.PrerequisiteData;
import in.ac.iitm.slotbook.model.PrerequisiteWarning;
import in.ac.iitm.slotbook.model.StudentSelection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Lists advisory prerequisite warnings for students' ranked courses.
 *
 * <p>Unlike {@link InputValidator}, nothing here is an error: prerequisites are advisory in v3 (P015),
 * so the result is reported alongside the slotbook and the solver input is left unchanged. The rule
 * and the messages match the portal's {@code api/_prerequisites.js}, so both report the same thing
 * for the same data:
 * <ul>
 *   <li>one {@code MISSING_PREREQUISITE} per ranked course and prerequisite not completed;</li>
 *   <li>a grade of U, W or I does not complete a course;</li>
 *   <li>a student with no completion record at all gets one {@code NO_COMPLETION_RECORD} instead.</li>
 * </ul>
 */
public final class PrerequisiteChecker {

    public List<PrerequisiteWarning> check(List<StudentSelection> selections, PrerequisiteData data) {
        List<PrerequisiteWarning> warnings = new ArrayList<>();
        Map<String, List<String>> rules = rules(data.prerequisites(), warnings);
        if (rules.isEmpty()) return List.copyOf(warnings);

        Map<String, Set<String>> completedByStudent = new HashMap<>();
        Set<String> studentsWithRecord = new HashSet<>();
        for (CompletedCourse completion : data.completions()) {
            studentsWithRecord.add(completion.studentId());
            if (completion.isCompleted()) {
                completedByStudent.computeIfAbsent(completion.studentId(), ignored -> new HashSet<>())
                        .add(completion.courseId());
            }
        }

        for (Map.Entry<String, List<StudentSelection>> entry : byStudentInRankOrder(selections).entrySet()) {
            String student = entry.getKey();
            List<String> constrained = entry.getValue().stream()
                    .map(StudentSelection::courseId)
                    .filter(rules::containsKey)
                    .toList();
            if (constrained.isEmpty()) continue;

            if (!studentsWithRecord.contains(student)) {
                warnings.add(new PrerequisiteWarning(student, PrerequisiteWarning.NO_RECORD,
                        String.join("|", constrained), "",
                        "No grade card on record, so prerequisites for " + String.join(", ", constrained)
                                + " could not be checked."));
                continue;
            }
            Set<String> completed = completedByStudent.getOrDefault(student, Set.of());
            for (String course : constrained) {
                for (String prerequisite : rules.get(course)) {
                    if (!completed.contains(prerequisite)) {
                        warnings.add(new PrerequisiteWarning(student, PrerequisiteWarning.MISSING, course, prerequisite,
                                course + " lists " + prerequisite
                                        + " as a prerequisite, which is not on your completed courses."));
                    }
                }
            }
        }
        return List.copyOf(warnings);
    }

    /** Course to its prerequisites, sorted; a course listed as its own prerequisite is reported and skipped. */
    private static Map<String, List<String>> rules(List<Prerequisite> prerequisites, List<PrerequisiteWarning> warnings) {
        Map<String, Set<String>> sorted = new TreeMap<>();
        for (Prerequisite rule : prerequisites) {
            if (rule.courseId().equals(rule.prerequisiteCourseId())) {
                warnings.add(new PrerequisiteWarning("", PrerequisiteWarning.SELF_PREREQUISITE, rule.courseId(),
                        rule.prerequisiteCourseId(), rule.courseId() + " lists itself as a prerequisite; rule ignored."));
                continue;
            }
            sorted.computeIfAbsent(rule.courseId(), ignored -> new TreeSet<>()).add(rule.prerequisiteCourseId());
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        sorted.forEach((course, list) -> result.put(course, List.copyOf(list)));
        return result;
    }

    /** Students in order of first appearance, each with selections sorted by rank. */
    private static Map<String, List<StudentSelection>> byStudentInRankOrder(List<StudentSelection> selections) {
        Map<String, List<StudentSelection>> byStudent = new LinkedHashMap<>();
        for (StudentSelection selection : selections) {
            byStudent.computeIfAbsent(selection.studentId(), ignored -> new ArrayList<>()).add(selection);
        }
        byStudent.values().forEach(list -> list.sort(Comparator.comparingInt(StudentSelection::rank)));
        return byStudent;
    }
}
