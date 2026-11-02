package in.ac.iitm.slotbook.cli;

import in.ac.iitm.slotbook.io.PrerequisiteInputs;
import in.ac.iitm.slotbook.io.SlotbookExporter;
import in.ac.iitm.slotbook.model.Course;
import in.ac.iitm.slotbook.model.PrerequisiteWarning;
import in.ac.iitm.slotbook.service.SlotbookRun;
import in.ac.iitm.slotbook.service.SlotbookService;
import in.ac.iitm.slotbook.solver.SolverChoice;

import java.nio.file.Files;
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
        Path prerequisites = null;
        Path completedCourses = null;
        boolean checkPrerequisites = true;
        try {
            for (int i = 0; i < args.length; i++) {
                if ("--input".equals(args[i]) && i + 1 < args.length) input = Path.of(args[++i]);
                else if ("--output".equals(args[i]) && i + 1 < args.length) output = Path.of(args[++i]);
                else if ("--solver".equals(args[i]) && i + 1 < args.length) solver = SolverChoice.fromFlag(args[++i]);
                else if ("--time-limit".equals(args[i]) && i + 1 < args.length) timeLimit = parseTimeLimit(args[++i]);
                else if ("--prerequisites".equals(args[i]) && i + 1 < args.length) prerequisites = existingFile(args[++i], "--prerequisites");
                else if ("--completed-courses".equals(args[i]) && i + 1 < args.length) completedCourses = existingFile(args[++i], "--completed-courses");
                else if ("--no-prerequisite-check".equals(args[i])) checkPrerequisites = false;
                else if (args[i].startsWith("--")) throw new IllegalArgumentException("Unknown or incomplete option: " + args[i] + " (see --help)");
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
            PrerequisiteInputs prerequisiteInputs = checkPrerequisites
                    ? PrerequisiteInputs.fromDirectory(input).overriddenBy(prerequisites, completedCourses)
                    : PrerequisiteInputs.none();
            SlotbookRun run = new SlotbookService(solver, timeLimit).run(input, prerequisiteInputs);
            System.out.printf("Solver: %s%nStatus: %s%nObjective: %d%nRuntime: %.3f s%n",
                    solver, run.solution().status(), run.solution().objectiveValue(), run.solution().runtimeSeconds());
            run.solution().diagnostics().forEach(message -> System.out.println("Diagnostic: " + message));
            printPrerequisiteWarnings(run);
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

    private static Path existingFile(String value, String option) {
        Path path = Path.of(value);
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException(option + " file not found: " + value);
        return path;
    }

    /** Warnings are advisory: printed and exported, but they never change the exit code. */
    private static void printPrerequisiteWarnings(SlotbookRun run) {
        if (!run.prerequisitesChecked()) return;
        System.out.printf("Prerequisite warnings: %d (advisory; the solver input is unchanged)%n",
                run.prerequisiteWarnings().size());
        for (PrerequisiteWarning warning : run.prerequisiteWarnings()) {
            String who = warning.studentId().isEmpty() ? "" : warning.studentId() + ": ";
            System.out.println("Warning [" + warning.code() + "] " + who + warning.message());
        }
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
                           [--prerequisites FILE] [--completed-courses FILE]
                           [--no-prerequisite-check]

                Options:
                  --input DIR                Directory containing the five CSV input files
                  --output DIR               Export directory (default: output)
                  --solver NAME              unitime (default, UniTime cpsolver) or cpsat (v1 OR-Tools)
                  --time-limit SECS          Solver time limit in seconds (default: 10)
                  --prerequisites FILE       course_id,prereq_course_id rules
                                             (default: DIR/prerequisites.csv if present)
                  --completed-courses FILE   student_id,course_id,grade[,term][,source]
                                             (default: DIR/completed_courses.csv if present)
                  --no-prerequisite-check    Skip prerequisite warnings even if the files exist
                  --help                     Show this help message

                Prerequisite warnings are advisory: they are printed and written to
                prerequisite_warnings.csv, but never block solving or change the exit
                status. Grades U, W and I do not count as completed. See slotbook(1).

                Exit status: 0 solved, 1 no feasible slotbook, 2 usage or input error.
                """);
    }
}

