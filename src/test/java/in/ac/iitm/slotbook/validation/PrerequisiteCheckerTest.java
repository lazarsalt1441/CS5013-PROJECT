package in.ac.iitm.slotbook.validation;

import in.ac.iitm.slotbook.model.CompletedCourse;
import in.ac.iitm.slotbook.model.Prerequisite;
import in.ac.iitm.slotbook.model.PrerequisiteData;
import in.ac.iitm.slotbook.model.PrerequisiteWarning;
import in.ac.iitm.slotbook.model.StudentSelection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the advisory prerequisite rule shared with the portal (P015). */
class PrerequisiteCheckerTest {
    private static final List<Prerequisite> RULES = List.of(
            new Prerequisite("EE5141", "EE3110"),
            new Prerequisite("EE5141", "EE1101"),
            new Prerequisite("EE5112", "EE3110"));

    private final PrerequisiteChecker checker = new PrerequisiteChecker();

    private static StudentSelection pick(String student, String course, int rank) {
        return new StudentSelection(student, course, rank);
    }

    private static CompletedCourse done(String student, String course, String grade) {
        return new CompletedCourse(student, course, grade, "05", "grade_card");
    }

    @Test
    void noWarningsWhenEveryPrerequisiteIsCompleted() {
        List<PrerequisiteWarning> warnings = checker.check(
                List.of(pick("S1", "EE5141", 1)),
                new PrerequisiteData(RULES, List.of(done("S1", "EE3110", "A"), done("S1", "EE1101", "B"))));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void reportsEachMissingPrerequisiteInRankThenCourseOrder() {
        List<PrerequisiteWarning> warnings = checker.check(
                List.of(pick("S1", "EE5112", 2), pick("S1", "EE5141", 1)),
                new PrerequisiteData(RULES, List.of(done("S1", "EE9999", "A"))));
        assertEquals(List.of("EE5141>EE1101", "EE5141>EE3110", "EE5112>EE3110"),
                warnings.stream().map(w -> w.courseId() + ">" + w.prerequisiteCourseId()).toList());
        assertTrue(warnings.stream().allMatch(w -> w.code().equals(PrerequisiteWarning.MISSING)));
        assertEquals("EE5141 lists EE1101 as a prerequisite, which is not on your completed courses.",
                warnings.get(0).message());
    }

    @Test
    void gradesUWAndIDoNotCompleteACourse() {
        for (String grade : List.of("U", "W", "I", "u")) {
            List<PrerequisiteWarning> warnings = checker.check(
                    List.of(pick("S1", "EE5112", 1)),
                    new PrerequisiteData(RULES, List.of(done("S1", "EE3110", grade))));
            assertEquals(1, warnings.size(), "grade " + grade);
            assertEquals(PrerequisiteWarning.MISSING, warnings.get(0).code());
        }
        for (String grade : List.of("S", "A", "B", "C", "D", "E", "P")) {
            assertTrue(done("S1", "EE3110", grade).isCompleted(), "grade " + grade);
        }
    }

    @Test
    void aRetakePassCountsEvenAfterAFail() {
        List<PrerequisiteWarning> warnings = checker.check(
                List.of(pick("S1", "EE5112", 1)),
                new PrerequisiteData(RULES, List.of(done("S1", "EE3110", "U"), done("S1", "EE3110", "C"))));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void studentWithoutAnyRecordGetsOneSummaryWarning() {
        List<PrerequisiteWarning> warnings = checker.check(
                List.of(pick("S9", "EE5141", 1), pick("S9", "EE0000", 2), pick("S9", "EE5112", 3)),
                new PrerequisiteData(RULES, List.of(done("S1", "EE3110", "A"))));
        assertEquals(1, warnings.size());
        assertEquals(PrerequisiteWarning.NO_RECORD, warnings.get(0).code());
        assertEquals("EE5141|EE5112", warnings.get(0).courseId());
        assertEquals("No grade card on record, so prerequisites for EE5141, EE5112 could not be checked.",
                warnings.get(0).message());
    }

    @Test
    void coursesWithoutRulesAndEmptyRuleSetsNeverWarn() {
        assertTrue(checker.check(List.of(pick("S1", "EE0000", 1)), new PrerequisiteData(RULES, List.of())).isEmpty());
        assertTrue(checker.check(List.of(pick("S1", "EE5141", 1)), PrerequisiteData.empty()).isEmpty());
        assertTrue(PrerequisiteData.empty().hasNoRules());
    }

    @Test
    void aSelfPrerequisiteIsReportedAndIgnored() {
        List<PrerequisiteWarning> warnings = checker.check(
                List.of(pick("S1", "EE7000", 1)),
                new PrerequisiteData(List.of(new Prerequisite("EE7000", "EE7000")), List.of()));
        assertEquals(1, warnings.size());
        assertEquals(PrerequisiteWarning.SELF_PREREQUISITE, warnings.get(0).code());
        assertFalse(warnings.get(0).message().isBlank());
    }

    @Test
    void studentsAreReportedInOrderOfFirstAppearance() {
        List<PrerequisiteWarning> warnings = checker.check(
                List.of(pick("S2", "EE5112", 1), pick("S1", "EE5112", 1)),
                new PrerequisiteData(RULES, List.of()));
        assertEquals(List.of("S2", "S1"), warnings.stream().map(PrerequisiteWarning::studentId).toList());
    }
}
