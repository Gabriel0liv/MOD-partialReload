package com.gabriel0liv.partialreload.core;

import com.gabriel0liv.partialreload.api.PreparedReloadArtifact;
import com.gabriel0liv.partialreload.change.ChangeSet;
import com.gabriel0liv.partialreload.joint.ActiveTagRecipeGeneration;
import com.gabriel0liv.partialreload.joint.TagRecipeCommitTransaction;
import com.gabriel0liv.partialreload.plan.ReloadPlan;
import com.gabriel0liv.partialreload.resource.ResourceSnapshot;

public record TagRecipeGameTestState(
        ResourceSnapshot activeReference, ResourceSnapshot latestScan, ChangeSet lastChangeSet,
        ReloadPlan lastPlan, String lastError, PreparedReloadArtifact preparedArtifact,
        TagRecipeCommitTransaction tagRecipeTransaction, ActiveTagRecipeGeneration retainedTagRecipeGeneration,
        ActiveTagRecipeGeneration activeTagRecipeGeneration, PartialReloadState state,
        ConnectedPlayerProbe connectedPlayerProbe, boolean safePointHeld,
        TagRecipeCurrentResourceProbe currentResourceProbe) {}
