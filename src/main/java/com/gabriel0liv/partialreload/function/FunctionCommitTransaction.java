package com.gabriel0liv.partialreload.function;

import com.gabriel0liv.partialreload.validation.ValidationReport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class FunctionCommitTransaction {
    private final UUID transactionId;
    private final UUID preparationId;
    private final Instant requestedAt;
    private final String requester;
    private final FunctionCommitPolicy policy;
    private final List<TransactionEvent> events = new ArrayList<>();
    private FunctionTransactionStatus status;
    private FunctionGeneration previousGeneration;
    private FunctionGeneration candidateGeneration;
    private ValidationReport validation = ValidationReport.VALID;

    public FunctionCommitTransaction(
            UUID transactionId,
            UUID preparationId,
            Instant requestedAt,
            String requester,
            FunctionCommitPolicy policy
    ) {
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.preparationId = preparationId;
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        this.requester = Objects.requireNonNull(requester, "requester");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.status = FunctionTransactionStatus.REQUESTED;
    }

    public UUID transactionId() { return transactionId; }
    public UUID preparationId() { return preparationId; }
    public Instant requestedAt() { return requestedAt; }
    public String requester() { return requester; }
    public FunctionCommitPolicy policy() { return policy; }
    public synchronized FunctionTransactionStatus status() { return status; }
    public synchronized FunctionGeneration previousGeneration() { return previousGeneration; }
    public synchronized FunctionGeneration candidateGeneration() { return candidateGeneration; }
    public synchronized List<TransactionEvent> events() { return List.copyOf(events); }
    public synchronized ValidationReport validation() { return validation; }

    public synchronized void status(FunctionTransactionStatus value) { status = value; }
    public synchronized void previousGeneration(FunctionGeneration value) { previousGeneration = value; }
    public synchronized void candidateGeneration(FunctionGeneration value) { candidateGeneration = value; }
    public synchronized void validation(ValidationReport value) { validation = value; }
    public synchronized void event(Instant at, TransactionEventType type, String detail) {
        events.add(new TransactionEvent(at, type, detail));
    }
}
