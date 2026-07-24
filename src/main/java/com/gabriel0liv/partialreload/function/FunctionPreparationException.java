package com.gabriel0liv.partialreload.function;

import com.gabriel0liv.partialreload.api.PartialReloadException;

public final class FunctionPreparationException extends PartialReloadException {
    public FunctionPreparationException(String code, String message) {
        super(code, message);
    }

    public FunctionPreparationException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
