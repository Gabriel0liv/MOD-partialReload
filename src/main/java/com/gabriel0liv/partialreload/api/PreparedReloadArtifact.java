package com.gabriel0liv.partialreload.api;

import com.gabriel0liv.partialreload.resource.ResourceSnapshot;
import com.gabriel0liv.partialreload.validation.ValidationReport;

import java.time.Instant;
import java.util.UUID;

public interface PreparedReloadArtifact {
    UUID preparationId();

    ReloadCategory category();

    Instant createdAt();

    ResourceSnapshot sourceSnapshot();

    ValidationReport validation();

    default boolean isApplicable() {
        return !validation().hasErrorsOrBlockers();
    }
}
