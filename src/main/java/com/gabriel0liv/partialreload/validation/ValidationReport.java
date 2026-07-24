package com.gabriel0liv.partialreload.validation;

import java.util.List;
import java.util.Objects;

public record ValidationReport(List<ValidationIssue> issues) {
    public static final ValidationReport VALID = new ValidationReport(List.of());

    public ValidationReport {
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    }

    public boolean hasBlockers() {
        return issues.stream().anyMatch(issue ->
                issue.severity() == ValidationSeverity.BLOCKER || issue.severity() == ValidationSeverity.ERROR);
    }
}
