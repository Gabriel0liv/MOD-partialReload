package com.gabriel0liv.partialreload.glm;

import com.gabriel0liv.partialreload.loot.ActiveLootDataGeneration;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class LootAndGlobalModifiersCommitTransaction {
    private final UUID transactionId;
    private final UUID preparationId;
    private final Instant createdAt;
    private final String requester;
    private final int lootManagerIdentity;
    private final int glmManagerIdentity;
    private final ActiveLootDataGeneration expectedLootGeneration;
    private final ActiveGlobalLootModifierGeneration expectedGlmGeneration;
    private GlobalLootModifierTransactionStatus status = GlobalLootModifierTransactionStatus.PREPARING;
    private ActiveLootDataGeneration previousLootGeneration;
    private ActiveLootDataGeneration candidateLootGeneration;
    private ActiveGlobalLootModifierGeneration previousGlmGeneration;
    private ActiveGlobalLootModifierGeneration candidateGlmGeneration;
    private boolean lootPublished;
    private boolean glmPublished;
    private boolean verificationPassed;
    private String failure;

    public LootAndGlobalModifiersCommitTransaction(UUID transactionId, UUID preparationId, Instant createdAt,
                                                   String requester, int lootManagerIdentity,
                                                   int glmManagerIdentity,
                                                   ActiveLootDataGeneration expectedLootGeneration,
                                                   ActiveGlobalLootModifierGeneration expectedGlmGeneration) {
        this.transactionId = Objects.requireNonNull(transactionId);
        this.preparationId = preparationId;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.requester = Objects.requireNonNull(requester);
        this.lootManagerIdentity = lootManagerIdentity;
        this.glmManagerIdentity = glmManagerIdentity;
        this.expectedLootGeneration = Objects.requireNonNull(expectedLootGeneration);
        this.expectedGlmGeneration = Objects.requireNonNull(expectedGlmGeneration);
    }
    public UUID transactionId(){return transactionId;} public UUID preparationId(){return preparationId;}
    public Instant createdAt(){return createdAt;} public String requester(){return requester;}
    public int lootManagerIdentity(){return lootManagerIdentity;} public int glmManagerIdentity(){return glmManagerIdentity;}
    public ActiveLootDataGeneration expectedLootGeneration(){return expectedLootGeneration;}
    public ActiveGlobalLootModifierGeneration expectedGlmGeneration(){return expectedGlmGeneration;}
    public GlobalLootModifierTransactionStatus status(){return status;} public void status(GlobalLootModifierTransactionStatus v){status=v;}
    public ActiveLootDataGeneration previousLootGeneration(){return previousLootGeneration;} public void previousLootGeneration(ActiveLootDataGeneration v){previousLootGeneration=v;}
    public ActiveLootDataGeneration candidateLootGeneration(){return candidateLootGeneration;} public void candidateLootGeneration(ActiveLootDataGeneration v){candidateLootGeneration=v;}
    public ActiveGlobalLootModifierGeneration previousGlmGeneration(){return previousGlmGeneration;} public void previousGlmGeneration(ActiveGlobalLootModifierGeneration v){previousGlmGeneration=v;}
    public ActiveGlobalLootModifierGeneration candidateGlmGeneration(){return candidateGlmGeneration;} public void candidateGlmGeneration(ActiveGlobalLootModifierGeneration v){candidateGlmGeneration=v;}
    public boolean lootPublished(){return lootPublished;} public void lootPublished(boolean v){lootPublished=v;}
    public boolean glmPublished(){return glmPublished;} public void glmPublished(boolean v){glmPublished=v;}
    public boolean verificationPassed(){return verificationPassed;} public void verificationPassed(boolean v){verificationPassed=v;}
    public String failure(){return failure;} public void failure(String v){failure=v;}
}
