package in.ac.iitm.slotbook.model;

/** IITM slot label and the weekly pattern represented by that label. */
public record Slot(String id, String days, String startTimes, String category) {}

