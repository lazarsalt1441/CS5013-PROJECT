package in.ac.iitm.slotbook.model;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * IITM slot label and the weekly pattern represented by that label.
 *
 * <p>{@code days}, {@code startTimes} and {@code lengths} are parallel pipe-separated lists, e.g.
 * {@code MON|TUE}, {@code 08:00|13:00}, {@code 50|50}. A blank {@code lengths} means 50-minute meetings.
 */
public record Slot(String id, String days, String startTimes, String lengths, String category) {
    public static final int DEFAULT_LENGTH_MINUTES = 50;
    /** Resolution of {@link #cells()}: one bit per five minutes of the week. */
    public static final int CELL_MINUTES = 5;
    public static final int CELLS_PER_DAY = 24 * 60 / CELL_MINUTES;
    private static final List<String> WEEKDAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    public Slot {
        lengths = lengths == null ? "" : lengths;
    }

    /** Convenience constructor for slots made only of default-length meetings. */
    public Slot(String id, String days, String startTimes, String category) {
        this(id, days, startTimes, "", category);
    }

    /** One weekly meeting of a slot. */
    public record Meeting(int dayIndex, int startMinute, int lengthMinutes) {
        public int endMinute() {
            return startMinute + lengthMinutes;
        }
    }

    public boolean isLab() {
        return "LAB".equalsIgnoreCase(category);
    }

    public List<Meeting> meetings() {
        String[] dayParts = days.split("\\|");
        String[] startParts = startTimes.split("\\|");
        String[] lengthParts = lengths.isBlank() ? new String[0] : lengths.split("\\|");
        if (dayParts.length != startParts.length || (lengthParts.length != 0 && lengthParts.length != dayParts.length)) {
            throw new IllegalArgumentException("Slot " + id + " has mismatched days, start_times and lengths");
        }
        List<Meeting> meetings = new ArrayList<>();
        for (int i = 0; i < dayParts.length; i++) {
            int day = WEEKDAYS.indexOf(dayParts[i].trim().toUpperCase());
            if (day < 0) throw new IllegalArgumentException("Slot " + id + " has unknown day " + dayParts[i]);
            String[] clock = startParts[i].trim().split(":");
            int start = Integer.parseInt(clock[0]) * 60 + Integer.parseInt(clock[1]);
            int length = lengthParts.length == 0 ? DEFAULT_LENGTH_MINUTES : Integer.parseInt(lengthParts[i].trim());
            meetings.add(new Meeting(day, start, length));
        }
        return meetings;
    }

    /** Weekly occupancy as a bitset of {@link #CELL_MINUTES}-minute cells; two slots clash iff these intersect. */
    public BitSet cells() {
        BitSet cells = new BitSet(WEEKDAYS.size() * CELLS_PER_DAY);
        for (Meeting meeting : meetings()) {
            int base = meeting.dayIndex() * CELLS_PER_DAY;
            int from = meeting.startMinute() / CELL_MINUTES;
            int to = (meeting.endMinute() + CELL_MINUTES - 1) / CELL_MINUTES;
            cells.set(base + from, base + to);
        }
        return cells;
    }

    public boolean overlaps(Slot other) {
        return cells().intersects(other.cells());
    }
}
