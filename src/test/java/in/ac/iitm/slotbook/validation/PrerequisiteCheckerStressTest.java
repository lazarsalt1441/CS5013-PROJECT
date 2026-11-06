package in.ac.iitm.slotbook.validation;

import in.ac.iitm.slotbook.io.PrerequisiteInputs;
import in.ac.iitm.slotbook.io.PrerequisiteLoader;
import in.ac.iitm.slotbook.model.CompletedCourse;
import in.ac.iitm.slotbook.model.Prerequisite;
import in.ac.iitm.slotbook.model.PrerequisiteData;
import in.ac.iitm.slotbook.model.PrerequisiteWarning;
import in.ac.iitm.slotbook.model.StudentSelection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stress tests for the advisory prerequisite rule (P017): many seeded random problems checked
 * against an independent naive oracle, input-order invariance, and volume with a scaling check
 * that does not depend on machine speed. Seeds are fixed, so a failure names a replayable seed.
 */
class PrerequisiteCheckerStressTest {
    private static final List<String> GRADES = List.of("S", "A", "B", "C", "D", "E", "P", "U", "W", "I");
    private final PrerequisiteChecker checker = new PrerequisiteChecker();

    private record Problem(List<StudentSelection> selections, PrerequisiteData data) {}

    private static String course(int i) {
        return "EE" + (1000 + i);
    }

    private static Problem randomProblem(long seed, int students, int courses, int rules) {
        Random random = new Random(seed);
        List<Prerequisite> prerequisites = new ArrayList<>();
        for (int i = 0; i < rules; i++) {
            // Occasional self-prerequisites and duplicates exercise SELF_PREREQUISITE and de-duplication.
            String target = course(random.nextInt(courses));
            String prerequisite = random.nextInt(50) == 0 ? target : course(random.nextInt(courses));
            prerequisites.add(new Prerequisite(target, prerequisite));
        }
        List<StudentSelection> selections = new ArrayList<>();
        List<CompletedCourse> completions = new ArrayList<>();
        for (int s = 0; s < students; s++) {
            String student = String.format("S%05d", s);
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < courses; i++) ids.add(i);
            Collections.shuffle(ids, random);
            int ranked = 1 + random.nextInt(10);
            for (int k = 0; k < ranked; k++) selections.add(new StudentSelection(student, course(ids.get(k)), k + 1));
            if (random.nextInt(5) > 0) {
                int done = random.nextInt(20);
                for (int k = 0; k < done; k++) {
                    String id = course(random.nextInt(courses));
                    completions.add(new CompletedCourse(student, id, GRADES.get(random.nextInt(GRADES.size())), "", "grade_card"));
                }
            }
        }
        // Selections arrive grouped by student but in arbitrary rank order, as a CSV might list them.
        Collections.shuffle(completions, random);
        return new Problem(selections, new PrerequisiteData(prerequisites, completions));
    }

    /** Deliberately naive restatement of the documented rule, written without reading the checker. */
    private static List<String> oracle(Problem problem) {
        List<String> out = new ArrayList<>();
        for (Prerequisite rule : problem.data().prerequisites()) {
            if (rule.courseId().equals(rule.prerequisiteCourseId())) {
                out.add("|" + PrerequisiteWarning.SELF_PREREQUISITE + "|" + rule.courseId() + "|" + rule.prerequisiteCourseId());
            }
        }
        Set<String> students = new LinkedHashSet<>();
        problem.selections().forEach(selection -> students.add(selection.studentId()));
        for (String student : students) {
            List<String> ranked = problem.selections().stream()
                    .filter(selection -> selection.studentId().equals(student))
                    .sorted((a, b) -> Integer.compare(a.rank(), b.rank()))
                    .map(StudentSelection::courseId)
                    .toList();
            List<CompletedCourse> records = problem.data().completions().stream()
                    .filter(completion -> completion.studentId().equals(student)).toList();
            Set<String> done = records.stream()
                    .filter(record -> !Set.of("U", "W", "I").contains(record.grade()))
                    .map(CompletedCourse::courseId).collect(Collectors.toSet());
            List<String> constrained = ranked.stream().filter(id -> problem.data().prerequisites().stream()
                    .anyMatch(rule -> rule.courseId().equals(id) && !rule.prerequisiteCourseId().equals(id))).toList();
            if (constrained.isEmpty()) continue;
            if (records.isEmpty()) {
                out.add(student + "|" + PrerequisiteWarning.NO_RECORD + "|" + String.join("|", constrained) + "|");
                continue;
            }
            for (String id : constrained) {
                Set<String> needs = new TreeSet<>();
                problem.data().prerequisites().stream()
                        .filter(rule -> rule.courseId().equals(id) && !rule.prerequisiteCourseId().equals(id))
                        .forEach(rule -> needs.add(rule.prerequisiteCourseId()));
                for (String prerequisite : needs) {
                    if (!done.contains(prerequisite)) {
                        out.add(student + "|" + PrerequisiteWarning.MISSING + "|" + id + "|" + prerequisite);
                    }
                }
            }
        }
        return out;
    }

    private static List<String> keys(List<PrerequisiteWarning> warnings) {
        return warnings.stream()
                .map(w -> w.studentId() + "|" + w.code() + "|" + w.courseId() + "|" + w.prerequisiteCourseId())
                .toList();
    }

    @Test
    void matchesAnIndependentOracleOnTwoHundredRandomProblems() {
        long total = 0;
        for (long seed = 1; seed <= 200; seed++) {
            Problem problem = randomProblem(seed, 60, 40, 60);
            List<String> expected = oracle(problem);
            assertEquals(expected, keys(checker.check(problem.selections(), problem.data())), "seed " + seed);
            total += expected.size();
        }
        assertTrue(total > 5_000, "random problems should produce many warnings, got " + total);
    }

    @Test
    void resultDoesNotDependOnTheOrderOfRulesOrCompletions() {
        for (long seed = 1; seed <= 30; seed++) {
            Problem problem = randomProblem(seed, 200, 50, 80);
            List<String> baseline = keys(checker.check(problem.selections(), problem.data()));
            List<Prerequisite> rules = new ArrayList<>(problem.data().prerequisites());
            List<CompletedCourse> completions = new ArrayList<>(problem.data().completions());
            Collections.shuffle(rules, new Random(seed * 31));
            Collections.shuffle(completions, new Random(seed * 37));
            List<String> reshuffled = keys(checker.check(problem.selections(), new PrerequisiteData(rules, completions)));
            // Self-prerequisite warnings follow rule order by design; everything else must be identical.
            assertEquals(withoutSelf(baseline), withoutSelf(reshuffled), "seed " + seed);
        }
    }

    private static List<String> withoutSelf(List<String> keys) {
        return keys.stream().filter(key -> !key.contains(PrerequisiteWarning.SELF_PREREQUISITE)).toList();
    }

    /**
     * Fastest of five runs after two warm-up runs, in milliseconds. The minimum is the standard way
     * to strip JIT-compilation and garbage-collection noise from a micro-benchmark; a median of
     * three measured 8x-24x for the same linear code on one machine.
     */
    private long fastestMillis(Runnable work) {
        work.run();
        work.run();
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            long start = System.nanoTime();
            work.run();
            best = Math.min(best, System.nanoTime() - start);
        }
        return Math.max(1, best / 1_000_000);
    }

    @Test
    void fiftyThousandStudentsCompleteAndScaleLinearly() {
        Problem small = randomProblem(7, 5_000, 150, 400);
        Problem large = randomProblem(7, 50_000, 150, 400);
        List<PrerequisiteWarning> warnings = checker.check(large.selections(), large.data());
        assertTrue(warnings.size() > 50_000, "large problem should produce many warnings, got " + warnings.size());
        // Equal total work: ten small runs against one run ten times larger. Linear code gives a
        // ratio near 1 (about 2 here, from CPU-cache effects at 50,000 students) and quadratic
        // code near 10; the limit of 5 separates them. Both sides are long enough (hundreds of
        // milliseconds) that scheduler noise on a shared CI runner cannot decide the result.
        long tenSmallMillis = fastestMillis(() -> {
            for (int i = 0; i < 10; i++) checker.check(small.selections(), small.data());
        });
        long oneLargeMillis = fastestMillis(() -> checker.check(large.selections(), large.data()));
        double ratio = (double) oneLargeMillis / tenSmallMillis;
        System.out.printf("Prerequisite checker: 1 x 50,000 students vs 10 x 5,000 -> %.2fx (%d ms vs %d ms)%n",
                ratio, oneLargeMillis, tenSmallMillis);
        assertTrue(ratio < 5, "the 10x problem cost " + ratio + "x the same work in small pieces; a quadratic step?");
    }

    @Test
    void loadsAHundredThousandCompletionRows(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("completed_courses.csv");
        Random random = new Random(11);
        try (BufferedWriter out = Files.newBufferedWriter(file)) {
            out.write("student_id,course_id,grade,term,source\n");
            for (int i = 0; i < 100_000; i++) {
                out.write(String.format("S%05d,%s,%s,%02d,grade_card%n",
                        i % 20_000, course(random.nextInt(500)), GRADES.get(random.nextInt(GRADES.size())), 1 + i % 8));
            }
        }
        PrerequisiteData data = new PrerequisiteLoader().load(new PrerequisiteInputs(null, file));
        assertEquals(100_000, data.completions().size());
        assertTrue(data.completions().stream().allMatch(c -> c.courseId().startsWith("EE") && c.studentId().startsWith("S")));
    }
}
