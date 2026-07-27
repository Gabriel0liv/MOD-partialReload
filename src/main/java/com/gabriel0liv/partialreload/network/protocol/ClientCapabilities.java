package com.gabriel0liv.partialreload.network.protocol;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.network.FriendlyByteBuf;

public final class ClientCapabilities {
    private final List<ClientCapability> ordered;

    private ClientCapabilities(Collection<ClientCapability> values) {
        EnumSet<ClientCapability> set = EnumSet.noneOf(ClientCapability.class);
        for (ClientCapability value : values) {
            if (value == null || !set.add(value)) {
                throw new IllegalArgumentException("CLIENT_CAPABILITY_DUPLICATE_OR_NULL");
            }
        }
        if (set.size() > ClientSyncProtocol.MAX_CAPABILITIES) {
            throw new IllegalArgumentException("CLIENT_CAPABILITY_LIMIT_EXCEEDED");
        }
        ordered = set.stream().sorted((a, b) -> Integer.compare(a.wireId(), b.wireId()))
                .toList();
    }

    public static ClientCapabilities empty() {
        return new ClientCapabilities(List.of());
    }

    public static ClientCapabilities of(ClientCapability... values) {
        return new ClientCapabilities(Arrays.asList(values));
    }

    public static ClientCapabilities fromWireIds(Collection<Integer> ids) {
        if (ids.size() > ClientSyncProtocol.MAX_CAPABILITIES) {
            throw new IllegalArgumentException("CLIENT_CAPABILITY_LIMIT_EXCEEDED");
        }
        return new ClientCapabilities(ids.stream().map(id -> {
            ClientCapability capability = ClientCapability.fromWireId(id);
            if (capability == null) {
                throw new IllegalArgumentException("TAG_RECIPE_CLIENT_CAPABILITY_MISSING");
            }
            return capability;
        }).toList());
    }

    public boolean contains(ClientCapability capability) {
        return ordered.contains(capability);
    }

    public List<ClientCapability> asList() {
        return ordered;
    }

    public Set<ClientCapability> asSet() {
        if (ordered.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(ordered));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(ordered.size());
        for (ClientCapability capability : ordered) {
            buffer.writeVarInt(capability.wireId());
        }
    }

    public static ClientCapabilities read(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > ClientSyncProtocol.MAX_CAPABILITIES) {
            throw new IllegalArgumentException("CLIENT_CAPABILITY_COUNT_INVALID");
        }
        java.util.ArrayList<Integer> ids = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(buffer.readVarInt());
        }
        return fromWireIds(ids);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ClientCapabilities that && ordered.equals(that.ordered);
    }

    @Override
    public int hashCode() {
        return ordered.hashCode();
    }

    @Override
    public String toString() {
        return ordered.stream().map(Enum::name).collect(Collectors.joining(",", "[", "]"));
    }
}
