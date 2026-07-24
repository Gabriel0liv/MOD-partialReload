package com.gabriel0liv.partialreload.api;

public enum ProviderCompatibility {
    PREPARE_SUPPORTED,
    SUPPORTED_READ_ONLY,
    PLANNED,
    RESTART_REQUIRED,
    UNKNOWN,
    INCOMPATIBLE
}
