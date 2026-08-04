package com.gabriel0liv.partialreload.loot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class LootDataCommitTransaction {
    private final UUID transactionId;
    private final UUID preparationId;
    private final Instant createdAt;
    private final String requester;
    private final int managerIdentity;
    private final String expectedActiveFingerprint;
    private final ActiveLootDataGeneration expectedActiveGeneration;
    private final List<String> events = new ArrayList<>();
    private LootDataTransactionStatus status = LootDataTransactionStatus.PREPARING;
    private ActiveLootDataGeneration previousGeneration;
    private ActiveLootDataGeneration candidateGeneration;
    private String failure;
    private boolean elementsPublished;
    private boolean typeIndexPublished;
    private boolean verificationPassed;
    private boolean rollbackPerformed;

    public LootDataCommitTransaction(UUID transactionId, UUID preparationId, Instant createdAt,
                                     String requester, int managerIdentity, String expectedActiveFingerprint,
                                     ActiveLootDataGeneration expectedActiveGeneration) {
        this.transactionId = Objects.requireNonNull(transactionId);
        this.preparationId = preparationId;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.requester = Objects.requireNonNull(requester);
        this.managerIdentity = managerIdentity;
        this.expectedActiveFingerprint = Objects.requireNonNull(expectedActiveFingerprint);
        this.expectedActiveGeneration = Objects.requireNonNull(expectedActiveGeneration);
    }

    public UUID transactionId() { return transactionId; }
    public UUID preparationId() { return preparationId; }
    public Instant createdAt() { return createdAt; }
    public String requester() { return requester; }
    public int managerIdentity() { return managerIdentity; }
    public String expectedActiveFingerprint() { return expectedActiveFingerprint; }
    public ActiveLootDataGeneration expectedActiveGeneration() { return expectedActiveGeneration; }
    public LootDataTransactionStatus status() { return status; }
    public void status(LootDataTransactionStatus value) { status = Objects.requireNonNull(value); events.add(value.name()); }
    public ActiveLootDataGeneration previousGeneration() { return previousGeneration; }
    public void previousGeneration(ActiveLootDataGeneration value) { previousGeneration = value; }
    public ActiveLootDataGeneration candidateGeneration() { return candidateGeneration; }
    public void candidateGeneration(ActiveLootDataGeneration value) { candidateGeneration = value; }
    public String failure() { return failure; }
    public void failure(String value) { failure = value; }
    public boolean elementsPublished() { return elementsPublished; }
    public void elementsPublished(boolean value) { elementsPublished = value; }
    public boolean typeIndexPublished() { return typeIndexPublished; }
    public void typeIndexPublished(boolean value) { typeIndexPublished = value; }
    public boolean verificationPassed() { return verificationPassed; }
    public void verificationPassed(boolean value) { verificationPassed = value; }
    public boolean rollbackPerformed() { return rollbackPerformed; }
    public void rollbackPerformed(boolean value) { rollbackPerformed = value; }
    public List<String> events() { return List.copyOf(events); }
}
