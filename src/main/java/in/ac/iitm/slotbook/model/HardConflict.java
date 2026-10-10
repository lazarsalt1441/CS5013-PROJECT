package in.ac.iitm.slotbook.model;

/** Two courses that must never receive the same primary slot. */
public record HardConflict(String course1, String course2, String reason) {
    public boolean contains(String courseId) {
        return course1.equals(courseId) || course2.equals(courseId);
    }

    public String other(String courseId) {
        if (course1.equals(courseId)) return course2;
        if (course2.equals(courseId)) return course1;
        throw new IllegalArgumentException(courseId + " is not part of this conflict");
    }
}

