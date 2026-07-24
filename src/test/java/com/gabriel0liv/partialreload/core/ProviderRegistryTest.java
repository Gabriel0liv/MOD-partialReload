package com.gabriel0liv.partialreload.core;

import com.gabriel0liv.partialreload.api.*;
import com.gabriel0liv.partialreload.change.ChangeSet;
import com.gabriel0liv.partialreload.plan.ProviderPlan;
import com.gabriel0liv.partialreload.validation.ValidationReport;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProviderRegistryTest {
    @Test
    void registersAndFindsProviderByCategory() {
        ProviderRegistry registry = new ProviderRegistry();
        ReloadProvider provider = new StubProvider(ResourceLocation.fromNamespaceAndPath("test", "provider"));

        registry.register(provider);

        assertSame(provider, registry.get(provider.id()).orElseThrow());
        assertEquals(java.util.List.of(provider), registry.providersFor(ReloadCategory.FUNCTIONS));
        assertTrue(registry.providersFor(ReloadCategory.RECIPES).isEmpty());
    }

    @Test
    void rejectsDuplicateProviderIds() {
        ProviderRegistry registry = new ProviderRegistry();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "duplicate");
        registry.register(new StubProvider(id));

        assertThrows(DuplicateProviderException.class, () -> registry.register(new StubProvider(id)));
    }

    private record StubProvider(ResourceLocation id) implements ReloadProvider {
        @Override
        public Set<ReloadCategory> categories() {
            return Set.of(ReloadCategory.FUNCTIONS);
        }

        @Override
        public ProviderCompatibility compatibility(ReloadEnvironment environment) {
            return ProviderCompatibility.SUPPORTED_READ_ONLY;
        }

        @Override
        public ScanResult scan(ScanContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ValidationReport validate(ValidationContext context, ChangeSet changeSet) {
            return ValidationReport.VALID;
        }

        @Override
        public ProviderPlan createPlan(PlanningContext context, ChangeSet changeSet) {
            throw new UnsupportedOperationException();
        }
    }
}
