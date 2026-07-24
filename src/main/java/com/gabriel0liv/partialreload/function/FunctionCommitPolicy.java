package com.gabriel0liv.partialreload.function;

public enum FunctionCommitPolicy {
    DO_NOT_RUN,
    RUN_NEWLY_ADDED,
    RUN_CHANGED_AND_ADDED,
    RUN_ALL;

    public boolean implemented() {
        return this == DO_NOT_RUN;
    }
}
