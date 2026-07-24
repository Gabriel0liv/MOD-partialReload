package com.gabriel0liv.partialreload.core;

import com.gabriel0liv.partialreload.api.PartialReloadException;
import com.gabriel0liv.partialreload.api.PlanningContext;
import com.gabriel0liv.partialreload.api.ProviderCompatibility;
import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.api.ReloadEnvironment;
import com.gabriel0liv.partialreload.api.ReloadProvider;
import com.gabriel0liv.partialreload.api.ScanContext;
import com.gabriel0liv.partialreload.api.ScanResult;
import com.gabriel0liv.partialreload.api.ValidationContext;
import com.gabriel0liv.partialreload.change.ChangeSet;
import com.gabriel0liv.partialreload.plan.ProviderPlan;
import com.gabriel0liv.partialreload.plan.ReloadPlan;
import com.gabriel0liv.partialreload.plan.ReloadPlanner;
import com.gabriel0liv.partialreload.resource.ResourceScanner;
import com.gabriel0liv.partialreload.validation.ValidationIssue;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import com.gabriel0liv.partialreload.validation.ValidationSeverity;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class VanillaDatapackProvider implements ReloadProvider {
    public static final ResourceLocation ID = ReloadPlanner.VANILLA_PROVIDER_ID;
    private static final Set<ReloadCategory> CATEGORIES =
            Set.copyOf(EnumSet.allOf(ReloadCategory.class));

    private final ResourceScanner scanner;

    public VanillaDatapackProvider(ResourceScanner scanner) {
        this.scanner = scanner;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Set<ReloadCategory> categories() {
        return CATEGORIES;
    }

    @Override
    public ProviderCompatibility compatibility(ReloadEnvironment environment) {
        return ProviderCompatibility.SUPPORTED_READ_ONLY;
    }

    @Override
    public ScanResult scan(ScanContext context) throws PartialReloadException {
        return new ScanResult(scanner.scan(context));
    }

    @Override
    public ValidationReport validate(ValidationContext context, ChangeSet changeSet) {
        List<ValidationIssue> issues = new ArrayList<>();
        changeSet.changedResources().forEach(change -> {
            if (change.category() == ReloadCategory.DYNAMIC_REGISTRIES) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.BLOCKER,
                        "RESTART_REQUIRED",
                        "Dynamic registry resource requires restart",
                        change.location()
                ));
            } else if (change.category() == ReloadCategory.UNKNOWN) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.BLOCKER,
                        "UNKNOWN_RESOURCE",
                        "No provider contract exists for this resource",
                        change.location()
                ));
            }
        });
        return new ValidationReport(issues);
    }

    @Override
    public ProviderPlan createPlan(PlanningContext context, ChangeSet changeSet) {
        ReloadPlan plan = new ReloadPlanner(context.clock(), UUID::randomUUID).createPlan(changeSet);
        return new ProviderPlan(
                ID,
                plan.categories(),
                plan.changedResources(),
                plan.risk(),
                plan.dependencies(),
                plan.warnings(),
                plan.blockers(),
                plan.supportStatus()
        );
    }
}
