package in.ac.iitm.slotbook.solver.unitime;

import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.CoursePair;
import in.ac.iitm.slotbook.model.HardConflict;
import in.ac.iitm.slotbook.model.Slot;
import in.ac.iitm.slotbook.model.TimeGrid;
import in.ac.iitm.slotbook.model.TimetableProblem;
import in.ac.iitm.slotbook.solver.Objective;
import org.cpsolver.coursett.Constants;
import org.cpsolver.coursett.constraint.GroupConstraint;
import org.cpsolver.coursett.constraint.InstructorConstraint;
import org.cpsolver.coursett.constraint.JenrlConstraint;
import org.cpsolver.coursett.constraint.RoomConstraint;
import org.cpsolver.coursett.model.Lecture;
import org.cpsolver.coursett.model.Placement;
import org.cpsolver.coursett.model.RoomLocation;
import org.cpsolver.coursett.model.Student;
import org.cpsolver.coursett.model.TimeLocation;
import org.cpsolver.coursett.model.TimetableModel;
import org.cpsolver.ifs.assignment.Assignment;
import org.cpsolver.ifs.util.DataProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates a {@link TimetableProblem} into a UniTime (cpsolver) course timetabling model.
 *
 * <p>Mapping:
 * <ul>
 *   <li>each course becomes a {@link Lecture} whose time domain is its allowed IITM slots, and each fixed
 *       lab becomes an extra single-placement lecture;</li>
 *   <li>an IITM slot meets at different times on different days, which one {@link TimeLocation} cannot
 *       express, so every slot shares the same day/start/length and its real meetings are encoded in the
 *       {@code weekCode} bitset (one bit per 5-minute cell of the week, see {@link Slot#cells()}). UniTime
 *       tests overlap as days &amp; hours &amp; weeks, so it then detects exactly the IITM overlaps;</li>
 *   <li>instructors, rooms, hard conflicts ({@code DIFF_TIME}, required) and student co-demand
 *       ({@link JenrlConstraint}) map to UniTime's own constraints;</li>
 *   <li>instructor and historical penalties become the placement's time preference, and each unit of
 *       student pair weight becomes one synthetic student, so UniTime's {@code TimePreferences} plus
 *       3 x {@code StudentOverlapConflict} equals the slotbook objective.</li>
 * </ul>
 */
public final class UniTimeModelBuilder {
    /** Shared day code and start/length; real times live in the weekCode bitset. */
    private static final int DAY_CODE = Constants.DAY_CODES[0];
    private static final int START_SLOT = 90;
    private static final int LENGTH = 1;
    private static final int UNLIMITED_ROOM_SIZE = 100_000;

    private final TimetableProblem problem;
    private final TimeGrid grid;
    private final Objective objective;
    private final Map<String, Long> slotIndexes = new HashMap<>();
    private final Map<String, Slot> slots;

    private final Map<String, Lecture> lectures = new LinkedHashMap<>();
    private final Map<String, List<Lecture>> partsByCourse = new LinkedHashMap<>();
    private final Map<Long, String> slotByPatternId = new HashMap<>();
    private long nextId = 1;

    public UniTimeModelBuilder(TimetableProblem problem) {
        this.problem = problem;
        this.grid = problem.timeGrid();
        this.objective = new Objective(problem);
        this.slots = problem.slotsById();
        for (int i = 0; i < problem.slots().size(); i++) {
            long patternId = i + 1L;
            slotIndexes.put(problem.slots().get(i).id(), patternId);
            slotByPatternId.put(patternId, problem.slots().get(i).id());
        }
    }

    /** Result of a build: the model plus the lookup from course id to its lecture variable. */
    public record Built(TimetableModel model, Map<String, Lecture> lectures, Map<Long, String> slotByPatternId) {
        /** IITM slot id of a placement produced by this model. */
        public String slotOf(Placement placement) {
            return slotByPatternId.get(placement.getTimeLocation().getDatePatternId());
        }
    }

    public Built build(DataProperties properties, Assignment<Lecture, Placement> assignment) {
        TimetableModel model = new TimetableModel(properties);
        Map<String, RoomConstraint> rooms = new HashMap<>();

        for (Course course : problem.courses()) {
            List<TimeLocation> times = new ArrayList<>();
            for (Slot slot : problem.slots()) {
                if (!course.allowedSlots().contains(slot.id())) continue;
                if (course.hasFixedSlot() && !course.fixedSlot().equals(slot.id())) continue;
                if (grid.overlapsOwnLab(course, slot.id())) continue;
                times.add(timeLocation(slot, objective.coursePenalty(course, slot.id())));
            }

            List<RoomLocation> roomLocations = new ArrayList<>();
            RoomConstraint roomConstraint = null;
            if (course.hasRoom()) {
                roomConstraint = rooms.computeIfAbsent(course.room().toUpperCase(), name -> {
                    RoomConstraint constraint = new RoomConstraint(
                            nextId++, course.room(), null, UNLIMITED_ROOM_SIZE, null, null, null, true, true);
                    model.addConstraint(constraint);
                    return constraint;
                });
                roomLocations.add(new RoomLocation(roomConstraint.getResourceId(), course.room(), null, 0,
                        UNLIMITED_ROOM_SIZE, null, null, true, roomConstraint));
            }

            Lecture lecture = new Lecture(nextId++, null, nextId++, course.id(), times, roomLocations,
                    roomLocations.size(), null, course.classSize(), course.classSize(), 1.0);
            if (!course.historicalSlot().isBlank() && slots.containsKey(course.historicalSlot())) {
                times.stream()
                        .filter(time -> course.historicalSlot().equals(slotByPatternId.get(time.getDatePatternId())))
                        .findFirst()
                        .ifPresent(time -> lecture.setInitialAssignment(
                                new Placement(lecture, time, roomLocations.isEmpty() ? null : roomLocations.get(0))));
            }
            model.addVariable(lecture);
            if (roomConstraint != null) roomConstraint.addVariable(lecture);
            lectures.put(course.id(), lecture);
            List<Lecture> parts = new ArrayList<>(List.of(lecture));

            if (course.hasLab()) {
                Slot lab = slots.get(course.additionalSlot());
                Lecture labLecture = new Lecture(nextId++, null, nextId++, course.id() + " lab " + lab.id(),
                        List.of(timeLocation(lab, 0)), List.of(), 0, null,
                        course.classSize(), course.classSize(), 1.0);
                model.addVariable(labLecture);
                parts.add(labLecture);
            }
            partsByCourse.put(course.id(), parts);
        }

        Map<String, InstructorConstraint> instructors = new LinkedHashMap<>();
        for (Course course : problem.courses()) {
            InstructorConstraint instructor = instructors.computeIfAbsent(course.instructor(), name -> {
                InstructorConstraint constraint = new InstructorConstraint(nextId++, name, name, true);
                model.addConstraint(constraint);
                return constraint;
            });
            partsByCourse.get(course.id()).forEach(instructor::addVariable);
        }

        for (Course course : problem.courses()) {
            List<Lecture> parts = partsByCourse.get(course.id());
            if (parts.size() > 1) addDifferentTime(model, parts);
        }
        for (HardConflict conflict : problem.hardConflicts()) {
            List<Lecture> parts = new ArrayList<>(partsByCourse.get(conflict.course1()));
            parts.addAll(partsByCourse.get(conflict.course2()));
            addDifferentTime(model, parts);
        }

        addStudents(model, assignment);
        return new Built(model, Map.copyOf(lectures), Map.copyOf(slotByPatternId));
    }

    private void addDifferentTime(TimetableModel model, List<Lecture> parts) {
        GroupConstraint constraint = new GroupConstraint(
                nextId++, GroupConstraint.ConstraintType.DIFF_TIME, Constants.sPreferenceRequired);
        model.addConstraint(constraint);
        parts.forEach(constraint::addVariable);
    }

    /** One synthetic student per unit of pair weight, enrolled in every part of both courses. */
    private void addStudents(TimetableModel model, Assignment<Lecture, Placement> assignment) {
        Map<Lecture, Map<Lecture, JenrlConstraint>> jenrls = new HashMap<>();
        for (Map.Entry<CoursePair, Integer> entry : problem.studentPairWeights().entrySet()) {
            List<Lecture> parts = new ArrayList<>(partsByCourse.get(entry.getKey().first()));
            parts.addAll(partsByCourse.get(entry.getKey().second()));
            for (int unit = 0; unit < entry.getValue(); unit++) {
                Student student = new Student(nextId++);
                for (Lecture lecture : parts) {
                    student.addLecture(lecture);
                    lecture.addStudent(assignment, student);
                }
                model.addStudent(student);
                for (int i = 0; i < parts.size(); i++) {
                    for (int j = i + 1; j < parts.size(); j++) {
                        Lecture first = parts.get(i).getId() < parts.get(j).getId() ? parts.get(i) : parts.get(j);
                        Lecture second = first == parts.get(i) ? parts.get(j) : parts.get(i);
                        JenrlConstraint jenrl = jenrls
                                .computeIfAbsent(first, ignored -> new HashMap<>())
                                .computeIfAbsent(second, ignored -> {
                                    JenrlConstraint constraint = new JenrlConstraint();
                                    constraint.addVariable(first);
                                    constraint.addVariable(second);
                                    model.addConstraint(constraint);
                                    return constraint;
                                });
                        jenrl.incJenrl(assignment, student);
                    }
                }
            }
        }
    }

    private TimeLocation timeLocation(Slot slot, int penalty) {
        long patternId = slotIndexes.get(slot.id());
        return new TimeLocation(DAY_CODE, START_SLOT, LENGTH, penalty, penalty, 0,
                patternId, slot.id(), slot.cells(), 0);
    }
}
