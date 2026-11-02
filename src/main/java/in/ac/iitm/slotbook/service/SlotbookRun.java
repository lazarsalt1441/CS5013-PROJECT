package in.ac.iitm.slotbook.service;

import in.ac.iitm.slotbook.model.PrerequisiteWarning;
import in.ac.iitm.slotbook.model.TimetableProblem;
import in.ac.iitm.slotbook.model.TimetableSolution;

import java.util.List;

/**
 * One solved dataset. {@code prerequisitesChecked} is true when prerequisite rules were supplied,
 * so an empty warning list then means "checked, nothing found" rather than "not checked".
 */
public record SlotbookRun(TimetableProblem problem, TimetableSolution solution,
                          List<PrerequisiteWarning> prerequisiteWarnings, boolean prerequisitesChecked) {

    public SlotbookRun {
        prerequisiteWarnings = List.copyOf(prerequisiteWarnings);
    }

    /** A run without prerequisite inputs (the v2 behaviour). */
    public SlotbookRun(TimetableProblem problem, TimetableSolution solution) {
        this(problem, solution, List.of(), false);
    }
}
