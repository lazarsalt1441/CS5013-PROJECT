package in.ac.iitm.slotbook.validation;

import in.ac.iitm.slotbook.io.DatasetLoader;
import in.ac.iitm.slotbook.model.TimetableProblem;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

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
}

