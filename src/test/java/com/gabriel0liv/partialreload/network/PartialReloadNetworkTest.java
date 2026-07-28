package com.gabriel0liv.partialreload.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import net.minecraftforge.network.NetworkRegistry;

class PartialReloadNetworkTest {
    @Test
    void acceptsOnlyTheCurrentProtocolAndForgeVanillaSentinels() {
        assertTrue(PartialReloadChannelCompatibility.acceptsNegotiatedVersion("1"));
        assertTrue(PartialReloadChannelCompatibility.acceptsNegotiatedVersion(NetworkRegistry.ACCEPTVANILLA));
        assertTrue(PartialReloadChannelCompatibility.acceptsNegotiatedVersion("ABSENT"));
        assertFalse(PartialReloadChannelCompatibility.acceptsNegotiatedVersion(null));
        assertFalse(PartialReloadChannelCompatibility.acceptsNegotiatedVersion(""));
        assertFalse(PartialReloadChannelCompatibility.acceptsNegotiatedVersion("0"));
        assertFalse(PartialReloadChannelCompatibility.acceptsNegotiatedVersion("2"));
        assertFalse(PartialReloadChannelCompatibility.acceptsNegotiatedVersion("invalid"));
    }

    @Test
    void channelUsesMissingPolicyOnBothDirections() throws Exception {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "gabriel0liv", "partialreload",
                "network", "PartialReloadNetwork.java"));
        String policy = "NetworkRegistry.acceptMissingOr(PartialReloadNetwork::acceptsNegotiatedVersion)";
        assertTrue(source.contains(policy));
        assertTrue(source.indexOf(policy) != source.lastIndexOf(policy));
    }
}
