package com.gabriel0liv.partialreload.joint;

import org.junit.jupiter.api.Test;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class UnsupportedRegistryMatrixTest {
    @Test
    void everyUnsupportedRegistryOperationAndNamespaceIsBlocked() {
        for (String registry : new String[]{"worldgen/biome", "damage_type"})
            for (var operation : TagRegistryMutationScopeResolver.Operation.values())
                for (String namespace : new String[]{"minecraft", "forge", "partialreload_test"}) {
                    assertFalse(TagRegistryMutationScopeResolver.supported(registry, operation, namespace));
                    assertTrue(TagRegistryMutationScopeResolver.blocker(registry, operation, namespace).startsWith("TAG_REGISTRY_COMMIT_UNSUPPORTED"));
                }
    }
}
