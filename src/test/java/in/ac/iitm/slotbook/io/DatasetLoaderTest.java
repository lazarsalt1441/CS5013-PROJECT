package in.ac.iitm.slotbook.io;

import in.ac.iitm.slotbook.model.CoursePair;
import in.ac.iitm.slotbook.model.TimetableProblem;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetLoaderTest {
    @Test
    void loadsAllFiveFixtureFiles() throws Exception {
        TimetableProblem problem = new DatasetLoader().load(Path.of("data/feasible"));
        assertEquals(18, problem.courses().size());
        assertEquals(17, problem.slots().size());
        assertEquals(11, problem.hardConflicts().size());
        assertEquals(20, problem.instructorPreferences().size());
        assertEquals(90, problem.studentSelections().size());
    }

    @Test
    void derivesPositiveCoDemandWeights() throws Exception {
        TimetableProblem problem = new DatasetLoader().load(Path.of("data/feasible"));
        assertFalse(problem.studentPairWeights().isEmpty());
        assertTrue(problem.studentPairWeights().get(new CoursePair("EE5150", "EE5178")) > 0);
    }
}

