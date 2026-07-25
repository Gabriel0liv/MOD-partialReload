package com.gabriel0liv.partialreload.kubejs;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class KubeJsScriptScannerTest {
    @Test
    void classifiesRecipeStartupAndClientScriptsWithoutExecutingThem() throws Exception {
        var root = Files.createTempDirectory("partialreload-kubejs");
        Files.createDirectories(root.resolve("server_scripts"));
        Files.createDirectories(root.resolve("startup_scripts"));
        Files.createDirectories(root.resolve("client_scripts"));
        Files.writeString(root.resolve("server_scripts/recipes.js"), "ServerEvents.recipes(e => e.shaped('minecraft:stick', []));");
        Files.writeString(root.resolve("startup_scripts/registries.js"), "StartupEvents.registry('item', e => e.create('x'));" );
        Files.writeString(root.resolve("client_scripts/ui.js"), "ClientEvents.tick(e => {});");

        var snapshot = new KubeJsScriptScanner().scan(root, 10, 100_000);
        assertEquals(KubeJsScriptClassification.RECIPE_EVENT_ONLY, snapshot.scripts().get("server_scripts/recipes.js").classification());
        assertEquals(KubeJsScriptClassification.STARTUP_SCRIPT, snapshot.scripts().get("startup_scripts/registries.js").classification());
        assertEquals(KubeJsScriptClassification.CLIENT_SCRIPT, snapshot.scripts().get("client_scripts/ui.js").classification());
        assertEquals(1, snapshot.recipeCandidates().size());
    }

    @Test
    void absentRuntimeProducesClosedDiagnostic() throws Exception {
        var inspection = new KubeJsRecipesProvider().inspect(Files.createTempDirectory("partialreload-kubejs-empty"), null, 10, 100_000);
        assertEquals(KubeJsRecipePreparationStatus.NOT_PRESENT, inspection.status());
        assertFalse(inspection.isApplicable());
        assertEquals(1, inspection.validation().count(com.gabriel0liv.partialreload.validation.ValidationSeverity.BLOCKER));
    }
}
