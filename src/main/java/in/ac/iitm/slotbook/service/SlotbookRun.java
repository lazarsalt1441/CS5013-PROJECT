package in.ac.iitm.slotbook.service;

import in.ac.iitm.slotbook.model.TimetableProblem;
import in.ac.iitm.slotbook.model.TimetableSolution;

public record SlotbookRun(TimetableProblem problem, TimetableSolution solution) {}

