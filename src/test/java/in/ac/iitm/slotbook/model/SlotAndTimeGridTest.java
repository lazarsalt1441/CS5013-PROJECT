package in.ac.iitm.slotbook.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Example-based tests added after SpotBugs and PIT (P019). */
class SlotAndTimeGridTest {

    @Test
    void aZeroLengthMeetingIsRejected() {
        // PIT survivor: "length <= 0" weakened to "length < 0".
        assertThrows(IllegalArgumentException.class, () -> new Slot("Z", "MON", "08:00", "0", "LECTURE").meetings());
    }

    @Test
    void dayNamesParseTheSameInEveryLocale() {
        // SpotBugs DM_CONVERT_CASE: in a Turkish locale "fri".toUpperCase() is "FRİ" (dotted I),
        // which used to make Friday an unknown day.
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(4, new Slot("F", "fri", "08:00", "LECTURE").meetings().get(0).dayIndex());
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    void blankOrMissingSlotsNeverOverlap() {
        // PIT NO_COVERAGE: the guard that treats "no slot" (e.g. a course without a lab) as no clash.
        TimeGrid grid = new TimeGrid(List.of(new Slot("A", "MON", "08:00", "LECTURE")));
        assertTrue(grid.overlaps("A", "A"));
        assertFalse(grid.overlaps("A", ""));
        assertFalse(grid.overlaps(null, "A"));
        assertFalse(grid.overlaps(" ", " "));
    }
}
