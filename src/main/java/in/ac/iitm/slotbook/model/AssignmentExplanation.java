package in.ac.iitm.slotbook.model;

import java.util.List;

/** Deterministic explanation generated from constraints and the solved assignment. */
public record AssignmentExplanation(
        String courseId,
        String assignedSlot,
        List<String> reasons,
        List<String> alternatives) {

    public AssignmentExplanation {
        reasons = List.copyOf(reasons);
        alternatives = List.copyOf(alternatives);
    }

    public String asText() {
        StringBuilder text = new StringBuilder(courseId)
                .append(" was assigned slot ").append(assignedSlot).append(".\n\nWhy this slot:\n");
        reasons.forEach(reason -> text.append("- ").append(reason).append('\n'));
        text.append("\nAlternative slots:\n");
        alternatives.forEach(alternative -> text.append("- ").append(alternative).append('\n'));
        return text.toString();
    }
}

