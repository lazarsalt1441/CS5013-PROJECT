package in.ac.iitm.slotbook.model;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared clash semantics for every solver, the validator and the explanations.
 *
 * <p>A course occupies its lecture slot plus, if it has one, its fixed lab ({@code additional_slot}).
 * These occupied slots are the course's <em>parts</em>. Two placed courses clash when any part of one
 * overlaps any part of the other in real weekly time; student penalties count every overlapping part pair.
 * Rooms belong to the lecture only, because lab rooms are not part of the input.
 */
public final class TimeGrid {
    private final Map<String, Slot> slots;
    private final Map<String, BitSet> cells = new HashMap<>();
    private final Map<String, Boolean> overlapCache = new HashMap<>();

    public TimeGrid(List<Slot> slots) {
        this.slots = new HashMap<>();
        for (Slot slot : slots) {
            this.slots.put(slot.id(), slot);
            cells.put(slot.id(), slot.cells());
        }
    }

    public boolean overlaps(String slotA, String slotB) {
        if (slotA == null || slotB == null || slotA.isBlank() || slotB.isBlank()) return false;
        return overlapCache.computeIfAbsent(slotA + "\u0000" + slotB, ignored -> {
            BitSet a = cells.get(slotA);
            BitSet b = cells.get(slotB);
            return a != null && b != null && a.intersects(b);
        });
    }

    /** Lecture slot followed by the lab slot, if the course has a lab. */
    public List<String> parts(Course course, String lectureSlot) {
        List<String> parts = new ArrayList<>(2);
        parts.add(lectureSlot);
        if (course.hasLab()) parts.add(course.additionalSlot());
        return parts;
    }

    /** Number of overlapping (part of a, part of b) pairs when a sits in slotA and b in slotB. */
    public int partOverlaps(Course a, String slotA, Course b, String slotB) {
        int count = 0;
        for (String partA : parts(a, slotA)) {
            for (String partB : parts(b, slotB)) {
                if (overlaps(partA, partB)) count++;
            }
        }
        return count;
    }

    public boolean clash(Course a, String slotA, Course b, String slotB) {
        return partOverlaps(a, slotA, b, slotB) > 0;
    }

    /** True when the lecture slot would overlap the course's own lab. */
    public boolean overlapsOwnLab(Course course, String lectureSlot) {
        return course.hasLab() && overlaps(lectureSlot, course.additionalSlot());
    }

    public boolean roomClash(Course a, String slotA, Course b, String slotB) {
        return a.hasRoom() && a.room().equalsIgnoreCase(b.room()) && overlaps(slotA, slotB);
    }

    public boolean isLab(String slotId) {
        Slot slot = slots.get(slotId);
        return slot != null && slot.isLab();
    }
}
