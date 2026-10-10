package in.ac.iitm.slotbook.model;

/** A precise input error presented before the solver is invoked. */
public record ValidationIssue(String code, String message) {}

