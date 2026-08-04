package com.gabriel0liv.partialreload.glm;

import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.concurrent.atomic.AtomicReference;

public final class GlobalLootModifierFaultInjection {
    private static final AtomicReference<GlobalLootModifierFaultPoint> ARMED = new AtomicReference<>();
    private GlobalLootModifierFaultInjection() {}

    public static void arm(GlobalLootModifierFaultPoint point) {
        if (FMLEnvironment.production) throw new IllegalStateException("GLM_FAULT_INJECTION_NOT_AVAILABLE");
        ARMED.set(point);
    }

    public static void hit(GlobalLootModifierFaultPoint point) {
        if (ARMED.compareAndSet(point, null)) {
            throw new IllegalStateException("GLM_FAULT_INJECTED:" + point);
        }
    }

    public static void clear() { ARMED.set(null); }
}
