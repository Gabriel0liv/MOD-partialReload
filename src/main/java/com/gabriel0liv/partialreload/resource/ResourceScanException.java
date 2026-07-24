package com.gabriel0liv.partialreload.resource;

import com.gabriel0liv.partialreload.api.PartialReloadException;

public final class ResourceScanException extends PartialReloadException {
    public ResourceScanException(String code, String message) {
        super(code, message);
    }

    public ResourceScanException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
