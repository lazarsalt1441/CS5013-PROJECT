package in.ac.iitm.slotbook.solver;

import in.ac.iitm.slotbook.solver.unitime.UniTimeTimetableSolver;

import java.util.Locale;

/** Solvers selectable from the CLI and the JavaFX app. */
public enum SolverChoice {
    /** UniTime's course timetabling engine (cpsolver); heuristic, scales well. Default. */
    UNITIME("unitime", "UniTime (cpsolver)"),
    /** v1 Google OR-Tools CP-SAT model; exact, proves optimality on small instances. */
    CPSAT("cpsat", "OR-Tools CP-SAT (v1)");

    private final String flag;
    private final String label;

    SolverChoice(String flag, String label) {
        this.flag = flag;
        this.label = label;
    }

    public String flag() {
        return flag;
    }

    public TimetableSolver create(double timeLimitSeconds) {
        return switch (this) {
            case UNITIME -> new UniTimeTimetableSolver(timeLimitSeconds, -1);
            case CPSAT -> new OrToolsTimetableSolver(timeLimitSeconds);
        };
    }

    public static SolverChoice fromFlag(String value) {
        for (SolverChoice choice : values()) {
            if (choice.flag.equals(value.toLowerCase(Locale.ROOT))) return choice;
        }
        throw new IllegalArgumentException("Unknown solver '" + value + "'; expected unitime or cpsat");
    }

    @Override
    public String toString() {
        return label;
    }
}
