package com.gabriel0liv.partialreload.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ClientHandshakeSideSafetyTest {
    @Test
    void commonSourcesDoNotImportMinecraftClient() throws Exception {
        Path root = Path.of("src", "main", "java");
        try (var paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("\\client\\"))
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path);
                            assertFalse(source.contains("import net.minecraft.client."), path.toString());
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    });
        }
        assertTrue(Files.exists(root.resolve("com/gabriel0liv/partialreload/network/PartialReloadNetwork.java")));
    }
}
