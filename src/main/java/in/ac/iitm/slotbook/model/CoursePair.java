package in.ac.iitm.slotbook.model;

/** Canonically ordered course pair used as a map key. */
public record CoursePair(String first, String second) {
    public CoursePair {
        if (first.compareTo(second) > 0) {
            String temporary = first;
            first = second;
            second = temporary;
        }
        if (first.equals(second)) {
            throw new IllegalArgumentException("A course pair requires two distinct courses");
        }
    }
}

