package com.gabriel0liv.partialreload.api;

import com.gabriel0liv.partialreload.change.ChangeSet;
import com.gabriel0liv.partialreload.plan.ProviderPlan;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public interface ReloadProvider {
    ResourceLocation id();

    Set<ReloadCategory> categories();

    ProviderCompatibility compatibility(ReloadEnvironment environment);

    ScanResult scan(ScanContext context) throws PartialReloadException;

    ValidationReport validate(ValidationContext context, ChangeSet changeSet);

    ProviderPlan createPlan(PlanningContext context, ChangeSet changeSet);
}
