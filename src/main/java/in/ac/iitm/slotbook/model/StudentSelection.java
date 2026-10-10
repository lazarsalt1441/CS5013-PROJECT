package in.ac.iitm.slotbook.model;

/** Synthetic or anonymized indication that a student intends to take a course. */
public record StudentSelection(String studentId, String courseId, int rank) {}

