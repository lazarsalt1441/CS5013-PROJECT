package in.ac.iitm.slotbook.validation;

import in.ac.iitm.slotbook.model.CompletedCourse;
import in.ac.iitm.slotbook.model.Prerequisite;
import in.ac.iitm.slotbook.model.PrerequisiteData;
import in.ac.iitm.slotbook.model.PrerequisiteWarning;
import in.ac.iitm.slotbook.model.StudentSelection;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Properties of the advisory prerequisite rule that must hold for every input (Module 5). */
class PrerequisiteCheckerPropertiesTest {
    private final PrerequisiteChecker checker = new PrerequisiteChecker();

    record Case(List<String> ranked, List<Prerequisite> rules, List<CompletedCourse> completions) {}

    private static String course(int i) {
        return "EE" + (1000 + i);
    }

    @Provide
    Arbitrary<Case> cases() {
        Arbitrary<List<String>> ranked = Arbitraries.integers().between(0, 11).set().ofMinSize(1).ofMaxSize(10)
                .map(set -> set.stream().map(PrerequisiteCheckerPropertiesTest::course).toList());
        Arbitrary<List<Prerequisite>> rules = Combinators.combine(
                Arbitraries.integers().between(0, 11), Arbitraries.integers().between(0, 11))
                .as((a, b) -> new Prerequisite(course(a), course(b))).list().ofMaxSize(15);
        Arbitrary<List<CompletedCourse>> completions = Combinators.combine(
                Arbitraries.integers().between(0, 11), Arbitraries.of("S", "A", "B", "C", "D", "E", "P", "U", "W", "I"))
                .as((c, g) -> new CompletedCourse("S1", course(c), g, "", "grade_card")).list().ofMaxSize(12);
        return Combinators.combine(ranked, rules, completions).as(Case::new);
    }

    private List<PrerequisiteWarning> check(Case c, List<CompletedCourse> completions) {
        List<StudentSelection> selections = IntStream.range(0, c.ranked().size())
                .mapToObj(i -> new StudentSelection("S1", c.ranked().get(i), i + 1)).toList();
        return checker.check(selections, new PrerequisiteData(c.rules(), completions));
    }

    @Property(tries = 1000)
    void warningsOnlyNameCoursesTheStudentRanked(@ForAll("cases") Case c) {
        for (PrerequisiteWarning w : check(c, c.completions())) {
            if (w.code().equals(PrerequisiteWarning.SELF_PREREQUISITE)) continue;
            for (String course : w.courseId().split("\\|")) {
                assertTrue(c.ranked().contains(course), w + " names unranked " + course);
            }
        }
    }

    @Property(tries = 1000)
    void passingAnotherCourseNeverAddsAMissingWarning(@ForAll("cases") Case c, @ForAll("courseIndex") int extra) {
        // With no record at all the checker gives one NO_COMPLETION_RECORD instead of per-course
        // warnings, so a first completion may legitimately reveal MISSING ones.
        if (c.completions().isEmpty()) return;
        Set<String> before = missing(check(c, c.completions()));
        List<CompletedCourse> more = new ArrayList<>(c.completions());
        more.add(new CompletedCourse("S1", course(extra), "A", "", "grade_card"));
        Set<String> after = missing(check(c, more));
        assertTrue(before.containsAll(after), "before " + before + ", after " + after);
    }

    @Property(tries = 1000)
    void aFailingGradeNeverRemovesAMissingWarning(@ForAll("cases") Case c, @ForAll("courseIndex") int extra,
                                                  @ForAll("failing") String grade) {
        // Only meaningful once the student has some record; NO_COMPLETION_RECORD is a separate case.
        if (c.completions().isEmpty()) return;
        Set<String> before = missing(check(c, c.completions()));
        List<CompletedCourse> more = new ArrayList<>(c.completions());
        more.add(new CompletedCourse("S1", course(extra), grade, "", "grade_card"));
        assertTrue(missing(check(c, more)).containsAll(before));
    }

    @Provide
    Arbitrary<Integer> courseIndex() {
        return Arbitraries.integers().between(0, 11);
    }

    @Provide
    Arbitrary<String> failing() {
        return Arbitraries.of("U", "W", "I", "u", "w");
    }

    private static Set<String> missing(List<PrerequisiteWarning> warnings) {
        return warnings.stream().filter(w -> w.code().equals(PrerequisiteWarning.MISSING))
                .map(w -> w.courseId() + ">" + w.prerequisiteCourseId()).collect(Collectors.toSet());
    }
}
