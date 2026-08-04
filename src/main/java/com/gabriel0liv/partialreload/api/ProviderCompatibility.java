package com.gabriel0liv.partialreload.api;

public enum ProviderCompatibility {
    COMMIT_SUPPORTED,
    PREPARE_SUPPORTED,
    SUPPORTED_READ_ONLY,
    PLANNED,
    RESTART_REQUIRED,
    UNKNOWN,
    INCOMPATIBLE
}
