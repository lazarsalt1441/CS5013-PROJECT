package in.ac.iitm.slotbook.model;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Property-based tests (CS5013 Module 5) for slot overlap, the basis of every clash rule.
 * jqwik generates the slots; the oracle is the textbook definition of overlapping intervals,
 * written without looking at {@link Slot#cells()}.
 */
class SlotPropertiesTest {

    private record M(int day, int start, int length) {}

    private static final List<String> DAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT");

    private static Slot slot(String id, List<M> meetings) {
        String days = meetings.stream().map(m -> DAYS.get(m.day())).collect(Collectors.joining("|"));
        String starts = meetings.stream().map(m -> String.format("%02d:%02d", m.start() / 60, m.start() % 60)).collect(Collectors.joining("|"));
        String lengths = meetings.stream().map(m -> String.valueOf(m.length())).collect(Collectors.joining("|"));
        return new Slot(id, days, starts, lengths, "LECTURE");
    }

    /** Oracle: two weekly patterns overlap iff some pair of meetings shares a day and an instant. */
    private static boolean oracle(List<M> a, List<M> b) {
        for (M x : a) {
            for (M y : b) {
                if (x.day() == y.day() && x.start() < y.start() + y.length() && y.start() < x.start() + x.length()) return true;
            }
        }
        return false;
    }

    private static Arbitrary<List<M>> meetings(int step) {
        Arbitrary<M> meeting = Combinators.combine(
                Arbitraries.integers().between(0, DAYS.size() - 1),
                Arbitraries.integers().between(7 * 60 / step, 19 * 60 / step).map(i -> i * step),
                Arbitraries.integers().between(10 / step == 0 ? 1 : 10 / step, 180 / step).map(i -> i * step))
                .as(M::new);
        return meeting.list().ofMinSize(1).ofMaxSize(3);
    }

    @Provide
    Arbitrary<List<M>> anyMinute() {
        return meetings(1);
    }

    @Provide
    Arbitrary<List<M>> onTheGrid() {
        return meetings(Slot.CELL_MINUTES);
    }

    @Property(tries = 2000)
    void overlapMatchesTheIntervalDefinitionAtAnyMinute(@ForAll("anyMinute") List<M> a, @ForAll("anyMinute") List<M> b) {
        assertEquals(oracle(a, b), slot("A", a).overlaps(slot("B", b)), () -> a + " vs " + b);
    }

    @Property(tries = 2000)
    void overlapMatchesTheIntervalDefinitionOnTheFiveMinuteGrid(@ForAll("onTheGrid") List<M> a, @ForAll("onTheGrid") List<M> b) {
        assertEquals(oracle(a, b), slot("A", a).overlaps(slot("B", b)), () -> a + " vs " + b);
    }

    @Property(tries = 1000)
    void overlapIsSymmetric(@ForAll("anyMinute") List<M> a, @ForAll("anyMinute") List<M> b) {
        assertEquals(slot("A", a).overlaps(slot("B", b)), slot("B", b).overlaps(slot("A", a)));
    }

    @Property(tries = 500)
    void everySlotOverlapsItself(@ForAll("anyMinute") List<M> a) {
        assertEquals(true, slot("A", a).overlaps(slot("A", a)));
    }
}
