package com.gabriel0liv.partialreload.api;

public class PartialReloadException extends Exception {
    private final String code;

    public PartialReloadException(String code, String message) {
        super(message);
        this.code = code;
    }

    public PartialReloadException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
