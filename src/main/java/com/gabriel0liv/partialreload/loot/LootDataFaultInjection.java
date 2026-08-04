package com.gabriel0liv.partialreload.loot;

import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.concurrent.atomic.AtomicReference;

/** One-shot userdev fault seam. */
public final class LootDataFaultInjection {
    private static final AtomicReference<LootDataFaultPoint> ARMED = new AtomicReference<>();

    private LootDataFaultInjection() {
    }

    public static void arm(LootDataFaultPoint point) {
        if (FMLEnvironment.production) throw new IllegalStateException("LOOT_FAULT_INJECTION_NOT_AVAILABLE_IN_PRODUCTION");
        ARMED.set(point);
    }

    public static void clear() {
        ARMED.set(null);
    }

    public static void hit(LootDataFaultPoint point) {
        if (ARMED.compareAndSet(point, null)) {
            throw new IllegalStateException("LOOT_FAULT_INJECTED:" + point);
        }
    }
}
