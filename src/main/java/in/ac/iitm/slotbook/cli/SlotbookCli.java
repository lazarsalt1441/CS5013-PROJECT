package in.ac.iitm.slotbook.cli;

import in.ac.iitm.slotbook.io.SlotbookExporter;
import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.service.SlotbookRun;
import in.ac.iitm.slotbook.service.SlotbookService;
import in.ac.iitm.slotbook.solver.SolverChoice;

import java.nio.file.Path;

/** Terminal entry point used in automated tests and headless demonstrations. */
public final class SlotbookCli {
    private SlotbookCli() {}

    public static void main(String[] args) {
        if (args.length == 0 || hasFlag(args, "--help")) {
            printHelp();
            return;
        }

        Path input = null;
        Path output = Path.of("output");
        SolverChoice solver = SolverChoice.UNITIME;
        double timeLimit = SlotbookService.DEFAULT_TIME_LIMIT_SECONDS;
        try {
            for (int i = 0; i < args.length; i++) {
                if ("--input".equals(args[i]) && i + 1 < args.length) input = Path.of(args[++i]);
                else if ("--output".equals(args[i]) && i + 1 < args.length) output = Path.of(args[++i]);
                else if ("--solver".equals(args[i]) && i + 1 < args.length) solver = SolverChoice.fromFlag(args[++i]);
                else if ("--time-limit".equals(args[i]) && i + 1 < args.length) timeLimit = parseTimeLimit(args[++i]);
                else if (!args[i].startsWith("--") && input == null) input = Path.of(args[i]);
            }
        } catch (IllegalArgumentException error) {
            System.err.println(error.getMessage());
            System.exit(2);
        }

        if (input == null) {
            System.err.println("Missing --input DATASET_DIRECTORY");
            System.exit(2);
        }

        try {
            SlotbookRun run = new SlotbookService(solver, timeLimit).run(input);
            System.out.printf("Solver: %s%nStatus: %s%nObjective: %d%nRuntime: %.3f s%n",
                    solver, run.solution().status(), run.solution().objectiveValue(), run.solution().runtimeSeconds());
            run.solution().diagnostics().forEach(message -> System.out.println("Diagnostic: " + message));
            if (!run.solution().hasSolution()) {
                System.exit(1);
            }
            System.out.println("\nCOURSE   SLOT  TITLE");
            for (Course course : run.problem().courses()) {
                System.out.printf("%-8s %-5s %s%n", course.id(),
                        run.solution().assignments().get(course.id()), course.title());
            }
            new SlotbookExporter().export(run, output);
            System.out.println("\nExported results to " + output.toAbsolutePath());
        } catch (Exception error) {
            System.err.println("Failed: " + error.getMessage());
            error.printStackTrace(System.err);
            System.exit(2);
        }
    }

    private static double parseTimeLimit(String value) {
        try {
            double seconds = Double.parseDouble(value);
            if (seconds > 0) return seconds;
        } catch (NumberFormatException ignored) {
            // reported below
        }
        throw new IllegalArgumentException("--time-limit must be a positive number of seconds, got " + value);
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String argument : args) if (flag.equals(argument)) return true;
        return false;
    }

    private static void printHelp() {
        System.out.println("""
                IITM Explainable Slotbook

                Usage:
                  slotbook --input DATASET_DIRECTORY [--output OUTPUT_DIRECTORY]
                           [--solver unitime|cpsat] [--time-limit SECONDS]

                Options:
                  --input DIR         Directory containing the five CSV input files
                  --output DIR        Export directory (default: output)
                  --solver NAME       unitime (default, UniTime cpsolver) or cpsat (v1 OR-Tools)
                  --time-limit SECS   Solver time limit in seconds (default: 10)
                  --help              Show this help message
                """);
    }
}

