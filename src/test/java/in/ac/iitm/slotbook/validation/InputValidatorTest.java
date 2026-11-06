package in.ac.iitm.slotbook.validation;

import in.ac.iitm.slotbook.io.DatasetLoader;
import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.InstructorPreference;
import in.ac.iitm.slotbook.model.StudentSelection;
import in.ac.iitm.slotbook.model.HardConflict;
import in.ac.iitm.slotbook.model.Slot;
import in.ac.iitm.slotbook.model.TimetableProblem;
import in.ac.iitm.slotbook.model.ValidationIssue;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputValidatorTest {
    @Test
    void feasibleFixtureHasNoValidationErrors() throws Exception {
        TimetableProblem problem = new DatasetLoader().load(Path.of("data/feasible"));
        assertTrue(new InputValidator().validate(problem).isEmpty());
    }

    @Test
    void identifiesContradictoryFixedAssignments() throws Exception {
        TimetableProblem problem = new DatasetLoader().load(Path.of("data/infeasible-fixed-conflict"));
        var issues = new InputValidator().validate(problem);
        assertEquals(1, issues.size());
        assertEquals("FIXED_CONFLICT", issues.get(0).code());
        assertTrue(issues.get(0).message().contains("EE9001"));
        assertTrue(issues.get(0).message().contains("EE9002"));
    }

    @Test
    void rejectsInconsistentPreferencesLabsAndHistory() {
        TimetableProblem problem = new TimetableProblem(
                List.of(
                        new Course("C1", "One", "G", "CORE", "Ann", 10, "Z", "", Set.of("A", "L"), "A", ""),
                        new Course("C2", "Two", "G", "CORE", "Bob", 10, "", "A", Set.of("A"), "L", "")),
                List.of(new Slot("A", "MON", "14:00", "LECTURE"), new Slot("L", "MON", "14:00", "165", "LAB")),
                List.of(),
                List.of(new InstructorPreference("Ann", "A", 1, "x"), new InstructorPreference("Ann", "A", -2, "y")),
                List.of());
        Set<String> codes = new InputValidator().validate(problem).stream()
                .map(ValidationIssue::code).collect(Collectors.toSet());
        assertEquals(Set.of("LAB_AS_LECTURE", "UNKNOWN_HISTORICAL_SLOT", "UNKNOWN_LAB_SLOT",
                "FIXED_OVERLAPS_LAB", "DUPLICATE_PREFERENCE", "NEGATIVE_PENALTY"), codes);
    }

    @Test
    void reportsSlotTimesOffTheFiveMinuteGrid() {
        // Regression for the jqwik finding in SlotPropertiesTest: off-grid times are invalid input.
        TimetableProblem problem = new TimetableProblem(
                List.of(), List.of(new Slot("A", "MON", "08:03", "LECTURE")), List.of(), List.of(), List.of());
        List<ValidationIssue> issues = new InputValidator().validate(problem);
        assertTrue(issues.stream().anyMatch(issue -> issue.code().equals("INVALID_SLOT_TIMES")
                && issue.message().contains("5-minute grid")), issues.toString());
    }

    // ---- Tests added after PIT mutation testing (P019): each kills a mutant that survived. ----

    private static final Slot LECTURE_A = new Slot("A", "MON", "08:00", "LECTURE");
    private static final Slot LECTURE_B = new Slot("B", "MON", "09:00", "LECTURE");
    private static final Slot LAB_TUE = new Slot("L1", "TUE", "14:00", "165", "LAB");
    private static final Slot LAB_TUE_TOO = new Slot("L2", "TUE", "14:00", "165", "LAB");
    private static final Slot LAB_WED = new Slot("L3", "WED", "14:00", "165", "LAB");

    private static Course course(String id, String instructor, String lecture, String lab) {
        return new Course(id, id, "G", "CORE", instructor, 10, "", "", Set.of(lecture), lab, "");
    }

    private static Set<String> codes(TimetableProblem problem) {
        return new InputValidator().validate(problem).stream().map(ValidationIssue::code).collect(Collectors.toSet());
    }

    @Test
    void aDuplicateSlotDoesNotHideOtherIssues() {
        // Survivor: the "stop early on INVALID_SLOT_TIMES" check replaced by "stop on any issue".
        Set<String> found = codes(new TimetableProblem(
                List.of(course("C1", "Ann", "Z", "")), List.of(LECTURE_A, LECTURE_A), List.of(), List.of(), List.of()));
        assertTrue(found.containsAll(Set.of("DUPLICATE_SLOT", "UNKNOWN_SLOT")), found.toString());
    }

    @Test
    void aBlankHistoricalSlotIsNotAnError() {
        // Survivor: the "historical slot is non-blank" condition negated.
        assertTrue(codes(new TimetableProblem(
                List.of(course("C1", "Ann", "A", "")), List.of(LECTURE_A), List.of(), List.of(), List.of())).isEmpty());
    }

    @Test
    void protectedPairsWithOverlappingLabsAreReported() {
        // Survivor: the lab-overlap condition negated; previously no test reached LAB_CONFLICT.
        List<Slot> slots = List.of(LECTURE_A, LECTURE_B, LAB_TUE, LAB_TUE_TOO, LAB_WED);
        HardConflict pair = new HardConflict("C1", "C2", "core pair");
        assertTrue(codes(new TimetableProblem(List.of(course("C1", "Ann", "A", "L1"), course("C2", "Bob", "B", "L2")),
                slots, List.of(pair), List.of(), List.of())).contains("LAB_CONFLICT"));
        assertFalse(codes(new TimetableProblem(List.of(course("C1", "Ann", "A", "L1"), course("C2", "Bob", "B", "L3")),
                slots, List.of(pair), List.of(), List.of())).contains("LAB_CONFLICT"));
    }

    @Test
    void anInstructorWithTwoOverlappingLabsIsReported() {
        // No test covered INSTRUCTOR_LAB_CONFLICT before (PIT: NO_COVERAGE).
        List<Slot> slots = List.of(LECTURE_A, LECTURE_B, LAB_TUE, LAB_TUE_TOO, LAB_WED);
        assertTrue(codes(new TimetableProblem(List.of(course("C1", "Ann", "A", "L1"), course("C2", "Ann", "B", "L2")),
                slots, List.of(), List.of(), List.of())).contains("INSTRUCTOR_LAB_CONFLICT"));
        assertFalse(codes(new TimetableProblem(List.of(course("C1", "Ann", "A", "L1"), course("C2", "Bob", "B", "L2")),
                slots, List.of(), List.of(), List.of())).contains("INSTRUCTOR_LAB_CONFLICT"));
        assertFalse(codes(new TimetableProblem(List.of(course("C1", "Ann", "A", "L1"), course("C2", "Ann", "B", "L3")),
                slots, List.of(), List.of(), List.of())).contains("INSTRUCTOR_LAB_CONFLICT"));
    }

    @Test
    void aStudentChoosingAnUnknownCourseIsReported() {
        // Survivor: the student-selection check removed entirely.
        assertTrue(codes(new TimetableProblem(List.of(course("C1", "Ann", "A", "")), List.of(LECTURE_A), List.of(),
                List.of(), List.of(new StudentSelection("S1", "NOPE", 1)))).contains("UNKNOWN_STUDENT_COURSE"));
    }
}
