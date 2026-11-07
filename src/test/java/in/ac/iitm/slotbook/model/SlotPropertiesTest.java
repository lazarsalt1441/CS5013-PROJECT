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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /** IITM's grid, and UniTime's time model, use 5-minute steps. */
    private static final int GRID = 5;

    @Provide
    Arbitrary<List<M>> onTheGrid() {
        return meetings(GRID);
    }

    @Provide
    Arbitrary<List<M>> offTheGrid() {
        return meetings(1).filter(list -> list.stream().anyMatch(m -> m.start() % GRID != 0 || m.length() % GRID != 0));
    }

    @Property(tries = 3000)
    void overlapMatchesTheIntervalDefinition(@ForAll("onTheGrid") List<M> a, @ForAll("onTheGrid") List<M> b) {
        assertEquals(oracle(a, b), slot("A", a).overlaps(slot("B", b)), () -> a + " vs " + b);
    }

    /**
     * Found by jqwik on the first run: at arbitrary minutes, meetings that only touch (07:01-07:21 and
     * 07:21-07:31) were reported as clashing, because cells round starts down and ends up. Off-grid times
     * are now rejected (and reported by the validator as INVALID_SLOT_TIMES) instead of silently rounded.
     */
    @Property(tries = 1000)
    void timesOffTheFiveMinuteGridAreRejected(@ForAll("offTheGrid") List<M> a) {
        assertThrows(IllegalArgumentException.class, () -> slot("A", a).meetings());
    }

    @Property(tries = 1000)
    void overlapIsSymmetric(@ForAll("onTheGrid") List<M> a, @ForAll("onTheGrid") List<M> b) {
        assertEquals(slot("A", a).overlaps(slot("B", b)), slot("B", b).overlaps(slot("A", a)));
    }

    @Property(tries = 500)
    void everySlotOverlapsItself(@ForAll("onTheGrid") List<M> a) {
        assertTrue(slot("A", a).overlaps(slot("A", a)));
    }
}
