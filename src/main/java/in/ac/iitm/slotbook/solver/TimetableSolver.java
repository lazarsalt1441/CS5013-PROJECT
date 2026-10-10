package in.ac.iitm.slotbook.solver;

import in.ac.iitm.slotbook.model.TimetableProblem;
import in.ac.iitm.slotbook.model.TimetableSolution;

public interface TimetableSolver {
    TimetableSolution solve(TimetableProblem problem);
}

