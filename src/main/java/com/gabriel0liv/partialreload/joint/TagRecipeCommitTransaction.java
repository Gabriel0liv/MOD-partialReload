package com.gabriel0liv.partialreload.joint;

import java.time.Instant; import java.util.*;

public final class TagRecipeCommitTransaction {
    private final UUID transactionId, preparationId; private final Instant requestedAt; private final String requester;
    private TagRecipeTransactionStatus status; private boolean tagMutationOccurred, recipeMutationOccurred, verificationPassed; private String failure;
    private ActiveTagRecipeGeneration previousGeneration, candidateGeneration;
    public TagRecipeCommitTransaction(UUID tx, UUID prep, Instant at, String requester){this.transactionId=tx;this.preparationId=prep;this.requestedAt=at;this.requester=requester;this.status=TagRecipeTransactionStatus.REQUESTED;}
    public UUID transactionId(){return transactionId;} public UUID preparationId(){return preparationId;} public Instant requestedAt(){return requestedAt;} public String requester(){return requester;}
    public TagRecipeTransactionStatus status(){return status;} public void status(TagRecipeTransactionStatus s){status=s;}
    public boolean tagMutationOccurred(){return tagMutationOccurred;} public void tagMutationOccurred(boolean v){tagMutationOccurred=v;}
    public boolean recipeMutationOccurred(){return recipeMutationOccurred;} public void recipeMutationOccurred(boolean v){recipeMutationOccurred=v;}
    public boolean verificationPassed(){return verificationPassed;} public void verificationPassed(boolean v){verificationPassed=v;}
    public String failure(){return failure;} public void failure(String value){failure=value;}
    public ActiveTagRecipeGeneration previousGeneration(){return previousGeneration;} public void previousGeneration(ActiveTagRecipeGeneration g){previousGeneration=g;}
    public ActiveTagRecipeGeneration candidateGeneration(){return candidateGeneration;} public void candidateGeneration(ActiveTagRecipeGeneration g){candidateGeneration=g;}
}
