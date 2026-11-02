package in.ac.iitm.slotbook.model;

import java.util.List;

/** Optional prerequisite inputs. Kept apart from {@link TimetableProblem} so the solver never sees them. */
public record PrerequisiteData(List<Prerequisite> prerequisites, List<CompletedCourse> completions) {

    public PrerequisiteData {
        prerequisites = List.copyOf(prerequisites);
        completions = List.copyOf(completions);
    }

    public static PrerequisiteData empty() {
        return new PrerequisiteData(List.of(), List.of());
    }

    /** True when there are no rules, so a check would have nothing to report. */
    public boolean hasNoRules() {
        return prerequisites.isEmpty();
    }
}
