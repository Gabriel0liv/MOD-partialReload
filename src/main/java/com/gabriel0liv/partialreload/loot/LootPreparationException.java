package com.gabriel0liv.partialreload.loot;

public final class LootPreparationException extends Exception {
    private final String code;

    public LootPreparationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public LootPreparationException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
