package com.gabriel0liv.partialreload.function;

import com.gabriel0liv.partialreload.validation.ValidationReport;

import java.util.UUID;

public record FunctionCommitResult(
        UUID transactionId,
        FunctionTransactionStatus status,
        boolean mutationOccurred,
        boolean verificationPassed,
        boolean rollbackAttempted,
        boolean rollbackSucceeded,
        ValidationReport report
) {
}
