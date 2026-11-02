package in.ac.iitm.slotbook.model;

/** A rule declared by a professor: {@code courseId} expects {@code prerequisiteCourseId} to be completed first. */
public record Prerequisite(String courseId, String prerequisiteCourseId) {}
