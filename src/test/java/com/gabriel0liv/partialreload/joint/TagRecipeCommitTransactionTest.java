package com.gabriel0liv.partialreload.joint;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TagRecipeCommitTransactionTest {
    @Test
    void tracksScopedAndPartialMutationState() {
        var tx = new TagRecipeCommitTransaction(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), "test");
        tx.registriesToMutate(List.of());
        tx.ingredientInvalidationOccurred(true);
        assertTrue(tx.registriesToMutate().isEmpty());
        assertTrue(tx.mutatedTagRegistries().isEmpty());
        assertFalse(tx.tagMutationOccurred());
        assertTrue(tx.ingredientInvalidationOccurred());
    }

    @Test
    void compatibilityRejectsUnavailableServer() {
        var compatibility = TagRecipeCommitCompatibility.inspect(null);
        assertFalse(compatibility.compatible());
        assertEquals("DISABLED", compatibility.supportLevel());
        assertTrue(compatibility.fingerprint().contains("unavailable"));
    }
}
