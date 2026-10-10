package in.ac.iitm.slotbook.testing;

import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.CoursePair;
import in.ac.iitm.slotbook.model.HardConflict;
import in.ac.iitm.slotbook.model.InstructorPreference;
import in.ac.iitm.slotbook.model.Slot;
import in.ac.iitm.slotbook.model.StudentSelection;
import in.ac.iitm.slotbook.model.TimetableProblem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Derives seeded dummy scenarios from a real dataset.
 *
 * <p>Every perturbation keeps a planted assignment feasible: restricted slot sets always contain
 * the planted slot, generated conflicts only join courses planted in different slots, and shared
 * instructors only teach courses planted in different slots. Generated problems are therefore
 * guaranteed solvable, and the planted penalty is an upper bound on the optimum.
 */
public final class ScenarioGenerator {
    private final TimetableProblem base;
    private final Map<String, String> planted;
    private final List<String> slotIds;
    private final List<String> instructors;

    /** @param planted a feasible assignment for {@code base}, typically the solver's baseline answer */
    public ScenarioGenerator(TimetableProblem base, Map<String, String> planted) {
        if (!ConstraintChecker.violations(base, planted).isEmpty()) {
            throw new IllegalArgumentException("Planted assignment is not feasible for the base problem");
        }
        this.base = base;
        this.planted = Map.copyOf(planted);
        this.slotIds = base.slots().stream().map(Slot::id).toList();
        this.instructors = base.courses().stream().map(Course::instructor).distinct().toList();
    }

    /** The fixed catalogue of named scenarios plus {@code randomCount} fully combined ones. */
    public List<Scenario> catalogue(long seed, int randomCount) {
        List<Scenario> scenarios = new ArrayList<>(List.of(
                scenario("baseline", "data/feasible unchanged", base),
                scenario("no-history", "historical slots cleared, so no stability penalty applies",
                        withCourses(base, clearHistory(base.courses()))),
                scenario("no-students", "student selections removed",
                        new TimetableProblem(base.courses(), base.slots(), base.hardConflicts(),
                                base.instructorPreferences(), List.of())),
                scenario("students-300", "300 synthetic students choosing 3-5 courses",
                        withStudents(base, syntheticStudents(new Random(seed), 300))),
                scenario("random-preferences", "every instructor gets random slot penalties 0-6",
                        withPreferences(base, randomPreferences(new Random(seed + 1)))),
                scenario("restricted-slots", "each unfixed course allows only 3 slots",
                        withCourses(base, restrictSlots(new Random(seed + 2), base.courses(), 3))),
                scenario("extra-conflicts", "10 additional generated hard conflicts",
                        withConflicts(base, extraConflicts(new Random(seed + 3), base, 10))),
                scenario("shared-instructors", "18 courses taught by 7 instructors",
                        withCourses(base, shareInstructors(new Random(seed + 4), base.courses(), 7)))));
        for (int i = 1; i <= randomCount; i++) {
            scenarios.add(combined("random-" + i, new Random(seed * 31 + i)));
        }
        return scenarios;
    }

    /** All perturbations applied at once with random intensities. */
    public Scenario combined(String name, Random random) {
        int students = 50 + random.nextInt(250);
        int allowed = 2 + random.nextInt(4);
        int conflicts = random.nextInt(15);
        int teachers = 6 + random.nextInt(instructors.size() - 5);

        List<Course> courses = shareInstructors(random, restrictSlots(random, base.courses(), allowed), teachers);
        TimetableProblem shaped = withCourses(base, courses);
        TimetableProblem problem = new TimetableProblem(
                courses,
                base.slots(),
                extraConflicts(random, shaped, conflicts),
                randomPreferences(random),
                syntheticStudents(random, students));
        return scenario(name, String.format(
                "%d students, %d allowed slots, +%d conflicts, %d instructors, random preferences",
                students, allowed, conflicts, teachers), problem);
    }

    /** A small random sub-problem that {@link BruteForceSolver} can check exhaustively. */
    public Scenario small(String name, Random random, int courseCount) {
        List<Course> pool = new ArrayList<>(base.courses());
        Collections.shuffle(pool, random);
        List<Course> chosen = pool.subList(0, courseCount);
        Set<String> ids = chosen.stream().map(Course::id).collect(Collectors.toSet());

        TimetableProblem trimmed = new TimetableProblem(
                restrictSlots(random, chosen, 3 + random.nextInt(4)),
                base.slots(),
                base.hardConflicts().stream()
                        .filter(c -> ids.contains(c.course1()) && ids.contains(c.course2())).toList(),
                randomPreferences(random),
                List.of());
        TimetableProblem problem = new TimetableProblem(
                trimmed.courses(),
                trimmed.slots(),
                extraConflicts(random, trimmed, random.nextInt(4)),
                trimmed.instructorPreferences(),
                syntheticStudents(random, 20 + random.nextInt(40)).stream()
                        .filter(s -> ids.contains(s.courseId())).toList());
        return scenario(name, courseCount + "-course brute-force sub-problem", problem);
    }

    private Scenario scenario(String name, String description, TimetableProblem problem) {
        Map<String, String> plantedHere = new LinkedHashMap<>();
        problem.courses().forEach(course -> plantedHere.put(course.id(), planted.get(course.id())));
        return new Scenario(name, description, problem, plantedHere);
    }

    private List<Course> clearHistory(List<Course> courses) {
        return courses.stream().map(c -> copy(c, c.instructor(), "", c.allowedSlots())).toList();
    }

    private List<Course> restrictSlots(Random random, List<Course> courses, int size) {
        List<Course> result = new ArrayList<>();
        for (Course course : courses) {
            if (course.hasFixedSlot()) {
                result.add(course);
                continue;
            }
            List<String> others = new ArrayList<>(slotIds);
            others.remove(planted.get(course.id()));
            Collections.shuffle(others, random);
            Set<String> allowed = new LinkedHashSet<>();
            allowed.add(planted.get(course.id()));
            allowed.addAll(others.subList(0, Math.min(size - 1, others.size())));
            result.add(copy(course, course.instructor(), course.historicalSlot(), allowed));
        }
        return result;
    }

    private List<HardConflict> extraConflicts(Random random, TimetableProblem problem, int count) {
        List<HardConflict> conflicts = new ArrayList<>(problem.hardConflicts());
        Set<CoursePair> existing = new HashSet<>();
        conflicts.forEach(c -> existing.add(new CoursePair(c.course1(), c.course2())));
        List<Course> courses = problem.courses();
        for (int attempt = 0, added = 0; added < count && attempt < count * 50; attempt++) {
            Course a = courses.get(random.nextInt(courses.size()));
            Course b = courses.get(random.nextInt(courses.size()));
            if (a.id().equals(b.id()) || planted.get(a.id()).equals(planted.get(b.id()))) continue;
            if (existing.add(new CoursePair(a.id(), b.id()))) {
                conflicts.add(new HardConflict(a.id(), b.id(), "Generated conflict"));
                added++;
            }
        }
        return conflicts;
    }

    private List<Course> shareInstructors(Random random, List<Course> courses, int teacherCount) {
        List<String> teachers = instructors.subList(0, Math.min(teacherCount, instructors.size()));
        Map<String, Set<String>> busySlots = new HashMap<>();
        List<Course> result = new ArrayList<>();
        for (Course course : courses) {
            String slot = planted.get(course.id());
            List<String> free = teachers.stream()
                    .filter(t -> !busySlots.getOrDefault(t, Set.of()).contains(slot)).toList();
            // A visiting instructor covers the course if every shared one is busy in this slot.
            String teacher = free.isEmpty() ? "Visiting " + course.id() : free.get(random.nextInt(free.size()));
            busySlots.computeIfAbsent(teacher, ignored -> new HashSet<>()).add(slot);
            result.add(copy(course, teacher, course.historicalSlot(), course.allowedSlots()));
        }
        return result;
    }

    private List<InstructorPreference> randomPreferences(Random random) {
        List<InstructorPreference> preferences = new ArrayList<>();
        for (String instructor : instructors) {
            List<String> slots = new ArrayList<>(slotIds);
            Collections.shuffle(slots, random);
            for (String slot : slots.subList(0, 2 + random.nextInt(3))) {
                preferences.add(new InstructorPreference(instructor, slot, random.nextInt(7), "Generated preference"));
            }
        }
        return preferences;
    }

    /** Students mostly pick courses from one semester group, plus one or two outside electives. */
    private List<StudentSelection> syntheticStudents(Random random, int count) {
        Map<String, List<String>> byGroup = new LinkedHashMap<>();
        base.courses().forEach(c -> byGroup.computeIfAbsent(c.semesterGroup(), ignored -> new ArrayList<>()).add(c.id()));
        List<List<String>> groups = new ArrayList<>(byGroup.values());
        List<String> all = base.courses().stream().map(Course::id).toList();

        List<StudentSelection> selections = new ArrayList<>();
        for (int s = 1; s <= count; s++) {
            List<String> home = new ArrayList<>(groups.get(random.nextInt(groups.size())));
            Collections.shuffle(home, random);
            Set<String> picks = new LinkedHashSet<>(home.subList(0, Math.min(home.size(), 2 + random.nextInt(2))));
            int target = picks.size() + 1 + random.nextInt(2);
            while (picks.size() < target) picks.add(all.get(random.nextInt(all.size())));
            int rank = 1;
            for (String course : picks) {
                selections.add(new StudentSelection(String.format("G%03d", s), course, rank++));
            }
        }
        return selections;
    }

    private static TimetableProblem withCourses(TimetableProblem p, List<Course> courses) {
        return new TimetableProblem(courses, p.slots(), p.hardConflicts(), p.instructorPreferences(), p.studentSelections());
    }

    private static TimetableProblem withStudents(TimetableProblem p, List<StudentSelection> students) {
        return new TimetableProblem(p.courses(), p.slots(), p.hardConflicts(), p.instructorPreferences(), students);
    }

    private static TimetableProblem withPreferences(TimetableProblem p, List<InstructorPreference> preferences) {
        return new TimetableProblem(p.courses(), p.slots(), p.hardConflicts(), preferences, p.studentSelections());
    }

    private static TimetableProblem withConflicts(TimetableProblem p, List<HardConflict> conflicts) {
        return new TimetableProblem(p.courses(), p.slots(), conflicts, p.instructorPreferences(), p.studentSelections());
    }

    private static Course copy(Course c, String instructor, String historicalSlot, Set<String> allowedSlots) {
        return new Course(c.id(), c.title(), c.semesterGroup(), c.courseType(), instructor, c.classSize(),
                historicalSlot, c.fixedSlot(), allowedSlots, c.additionalSlot(), c.room());
    }
}
