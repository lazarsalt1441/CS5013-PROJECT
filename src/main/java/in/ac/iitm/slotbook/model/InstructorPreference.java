package in.ac.iitm.slotbook.model;

/** Non-negative soft penalty for assigning an instructor to a slot. */
public record InstructorPreference(String instructor, String slotId, int penalty, String reason) {}

