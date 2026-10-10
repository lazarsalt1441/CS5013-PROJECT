package in.ac.iitm.slotbook.model;

import java.util.Set;

/** A course that needs one primary institutional slot. */
public record Course(
        String id,
        String title,
        String semesterGroup,
        String courseType,
        String instructor,
        int classSize,
        String historicalSlot,
        String fixedSlot,
        Set<String> allowedSlots,
        String additionalSlot,
        String room) {

    public Course {
        allowedSlots = Set.copyOf(allowedSlots);
    }

    public boolean hasFixedSlot() {
        return fixedSlot != null && !fixedSlot.isBlank();
    }

    /** True when the course has a fixed lab meeting in {@code additionalSlot}. */
    public boolean hasLab() {
        return additionalSlot != null && !additionalSlot.isBlank();
    }

    public boolean hasRoom() {
        return room != null && !room.isBlank();
    }
}

