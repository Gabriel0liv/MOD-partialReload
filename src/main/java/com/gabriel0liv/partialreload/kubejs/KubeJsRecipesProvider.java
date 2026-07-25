package com.gabriel0liv.partialreload.kubejs;

import com.gabriel0liv.partialreload.api.ReloadCategory;
import com.gabriel0liv.partialreload.recipe.PreparedRecipes;
import com.gabriel0liv.partialreload.validation.*;

import java.nio.file.Path;
import java.util.ArrayList;

/** Optional boundary. It never invokes KubeJS or touches the active runtime. */
public final class KubeJsRecipesProvider {
    public KubeJsRecipePreparationStatus status() { return KubeJsRecipePreparationStatus.NOT_PRESENT; }

    public KubeJsInspection inspect(Path kubeJsRoot, PreparedRecipes baseline, int maxFiles, long maxBytes) {
        ArrayList<ValidationIssue> issues = new ArrayList<>();
        KubeJsScriptSnapshot snapshot;
        try { snapshot = new KubeJsScriptScanner().scan(kubeJsRoot, maxFiles, maxBytes); }
        catch (Exception ex) {
            snapshot = new KubeJsScriptSnapshot(java.time.Instant.now(), java.util.Map.of());
            issues.add(new ValidationIssue(ValidationSeverity.BLOCKER, "KUBEJS_LIMIT_EXCEEDED", ReloadCategory.RECIPES, null, null, null, ex.getMessage(), null, ex.toString()));
        }
        if (status() == KubeJsRecipePreparationStatus.NOT_PRESENT)
            issues.add(new ValidationIssue(ValidationSeverity.BLOCKER, "KUBEJS_NOT_PRESENT", ReloadCategory.RECIPES, null, null, null, "KubeJS Forge 1.20.1 runtime is not available; no script was executed.", null, null));
        return new KubeJsInspection(status(), baseline, snapshot, new ValidationReport(issues));
    }

    public record KubeJsInspection(KubeJsRecipePreparationStatus status, PreparedRecipes baseline,
                                   KubeJsScriptSnapshot scripts, ValidationReport validation) {
        public boolean isApplicable() { return !validation.hasErrorsOrBlockers(); }
    }
}
