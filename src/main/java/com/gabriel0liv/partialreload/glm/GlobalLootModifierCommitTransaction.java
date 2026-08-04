package com.gabriel0liv.partialreload.glm;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class GlobalLootModifierCommitTransaction {
    private final UUID transactionId;
    private final UUID preparationId;
    private final Instant createdAt;
    private final String requester;
    private final int managerIdentity;
    private final ActiveGlobalLootModifierGeneration expectedActiveGeneration;
    private final List<String> events = new ArrayList<>();
    private GlobalLootModifierTransactionStatus status = GlobalLootModifierTransactionStatus.PREPARING;
    private ActiveGlobalLootModifierGeneration previousGeneration;
    private ActiveGlobalLootModifierGeneration candidateGeneration;
    private String failure;
    private boolean published;
    private boolean verificationPassed;
    private boolean rollbackPerformed;

    public GlobalLootModifierCommitTransaction(UUID transactionId, UUID preparationId, Instant createdAt,
                                               String requester, int managerIdentity,
                                               ActiveGlobalLootModifierGeneration expectedActiveGeneration) {
        this.transactionId = Objects.requireNonNull(transactionId);
        this.preparationId = preparationId;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.requester = Objects.requireNonNull(requester);
        this.managerIdentity = managerIdentity;
        this.expectedActiveGeneration = Objects.requireNonNull(expectedActiveGeneration);
    }

    public UUID transactionId() { return transactionId; }
    public UUID preparationId() { return preparationId; }
    public Instant createdAt() { return createdAt; }
    public String requester() { return requester; }
    public int managerIdentity() { return managerIdentity; }
    public ActiveGlobalLootModifierGeneration expectedActiveGeneration() { return expectedActiveGeneration; }
    public GlobalLootModifierTransactionStatus status() { return status; }
    public void status(GlobalLootModifierTransactionStatus value) { status = Objects.requireNonNull(value); events.add(value.name()); }
    public ActiveGlobalLootModifierGeneration previousGeneration() { return previousGeneration; }
    public void previousGeneration(ActiveGlobalLootModifierGeneration value) { previousGeneration = value; }
    public ActiveGlobalLootModifierGeneration candidateGeneration() { return candidateGeneration; }
    public void candidateGeneration(ActiveGlobalLootModifierGeneration value) { candidateGeneration = value; }
    public String failure() { return failure; }
    public void failure(String value) { failure = value; }
    public boolean published() { return published; }
    public void published(boolean value) { published = value; }
    public boolean verificationPassed() { return verificationPassed; }
    public void verificationPassed(boolean value) { verificationPassed = value; }
    public boolean rollbackPerformed() { return rollbackPerformed; }
    public void rollbackPerformed(boolean value) { rollbackPerformed = value; }
    public List<String> events() { return List.copyOf(events); }
}
