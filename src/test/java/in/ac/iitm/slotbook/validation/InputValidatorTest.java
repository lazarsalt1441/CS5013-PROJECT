package in.ac.iitm.slotbook.validation;

import in.ac.iitm.slotbook.io.DatasetLoader;
import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.InstructorPreference;
import in.ac.iitm.slotbook.model.Slot;
import in.ac.iitm.slotbook.model.TimetableProblem;
import in.ac.iitm.slotbook.model.ValidationIssue;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}

