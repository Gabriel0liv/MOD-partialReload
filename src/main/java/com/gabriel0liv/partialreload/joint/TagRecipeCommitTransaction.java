package com.gabriel0liv.partialreload.joint;

import java.time.Instant; import java.util.*;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

public final class TagRecipeCommitTransaction {
    private final UUID transactionId, preparationId; private final Instant requestedAt; private final String requester;
    private TagRecipeTransactionStatus status; private boolean tagMutationOccurred, recipeMutationOccurred, verificationPassed, restartRequired; private String failure;
    private boolean ingredientInvalidationOccurred, tagsUpdatedEventDispatched;
    private int ingredientCommitInvalidations, ingredientRollbackInvalidations, commitTagEvents, rollbackTagEvents;
    private final Set<ResourceKey<? extends Registry<?>>> registriesToMutate = new LinkedHashSet<>();
    private final Set<ResourceKey<? extends Registry<?>>> mutatedTagRegistries = new LinkedHashSet<>();
    private ActiveTagRecipeGeneration previousGeneration, candidateGeneration;
    private int recipeManagerIdentity, registryAccessIdentity;
    private String compatibilityFingerprint;
    private final List<TagRecipeTransactionEvent> events = new ArrayList<>();
    public TagRecipeCommitTransaction(UUID tx, UUID prep, Instant at, String requester){this.transactionId=tx;this.preparationId=prep;this.requestedAt=at;this.requester=requester;this.status=TagRecipeTransactionStatus.REQUESTED;}
    public UUID transactionId(){return transactionId;} public UUID preparationId(){return preparationId;} public Instant requestedAt(){return requestedAt;} public String requester(){return requester;}
    public TagRecipeTransactionStatus status(){return status;} public void status(TagRecipeTransactionStatus s){status=s; events.add(new TagRecipeTransactionEvent(Instant.now(), transactionId, s, TagRecipeTransactionEventType.STATUS_CHANGED, null));}
    public void event(TagRecipeTransactionEventType type, TagRecipeTransactionStatus s, String detail){
        events.add(new TagRecipeTransactionEvent(Instant.now(), transactionId, s, Objects.requireNonNull(type), detail));
    }
    public List<TagRecipeTransactionEvent> events(){return List.copyOf(events);}
    public boolean tagMutationOccurred(){return tagMutationOccurred;} public void tagMutationOccurred(boolean v){tagMutationOccurred=v;}
    public boolean recipeMutationOccurred(){return recipeMutationOccurred;} public void recipeMutationOccurred(boolean v){recipeMutationOccurred=v;}
    public boolean recipePublicationOccurred(){return recipeMutationOccurred;}
    public boolean verificationPassed(){return verificationPassed;} public void verificationPassed(boolean v){verificationPassed=v;}
    public boolean restartRequired(){return restartRequired;} public void restartRequired(boolean value){restartRequired=value;}
    public String failure(){return failure;} public void failure(String value){failure=value;}
    public boolean ingredientInvalidationOccurred(){return ingredientInvalidationOccurred;} public void ingredientInvalidationOccurred(boolean value){ingredientInvalidationOccurred=value;}
    public boolean tagsUpdatedEventDispatched(){return tagsUpdatedEventDispatched;} public void tagsUpdatedEventDispatched(boolean value){tagsUpdatedEventDispatched=value;}
    public int ingredientCommitInvalidations(){return ingredientCommitInvalidations;} public void ingredientCommitInvalidations(int value){ingredientCommitInvalidations=value;}
    public int ingredientRollbackInvalidations(){return ingredientRollbackInvalidations;} public void ingredientRollbackInvalidations(int value){ingredientRollbackInvalidations=value;}
    public int commitTagEvents(){return commitTagEvents;} public void commitTagEvents(int value){commitTagEvents=value;}
    public int rollbackTagEvents(){return rollbackTagEvents;} public void rollbackTagEvents(int value){rollbackTagEvents=value;}
    public Set<ResourceKey<? extends Registry<?>>> registriesToMutate(){return Set.copyOf(registriesToMutate);}
    public void registriesToMutate(Collection<ResourceKey<? extends Registry<?>>> values){registriesToMutate.clear(); registriesToMutate.addAll(values);}
    public Set<ResourceKey<? extends Registry<?>>> mutatedTagRegistries(){return Set.copyOf(mutatedTagRegistries);}
    public void tagRegistryMutated(ResourceKey<? extends Registry<?>> key){mutatedTagRegistries.add(key); tagMutationOccurred=true;}
    public ActiveTagRecipeGeneration previousGeneration(){return previousGeneration;} public void previousGeneration(ActiveTagRecipeGeneration g){previousGeneration=g;}
    public ActiveTagRecipeGeneration candidateGeneration(){return candidateGeneration;} public void candidateGeneration(ActiveTagRecipeGeneration g){candidateGeneration=g;}
    public int recipeManagerIdentity(){return recipeManagerIdentity;} public void recipeManagerIdentity(int value){recipeManagerIdentity=value;}
    public int registryAccessIdentity(){return registryAccessIdentity;} public void registryAccessIdentity(int value){registryAccessIdentity=value;}
    public String compatibilityFingerprint(){return compatibilityFingerprint;} public void compatibilityFingerprint(String value){compatibilityFingerprint=value;}
}
