package com.gabriel0liv.partialreload.plan;

public enum ReloadRisk {
    LOW,
    MODERATE,
    HIGH,
    UNKNOWN,
    RESTART_REQUIRED;

    public static ReloadRisk max(ReloadRisk left, ReloadRisk right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }
}
