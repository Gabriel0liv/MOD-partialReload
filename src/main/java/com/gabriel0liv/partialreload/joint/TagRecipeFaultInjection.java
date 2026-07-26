package com.gabriel0liv.partialreload.joint;

import net.minecraftforge.fml.loading.FMLEnvironment;

/** Userdev-only deterministic fault seam; never active in production. */
public final class TagRecipeFaultInjection {
    private static volatile TagRecipeFaultPoint point;
    private TagRecipeFaultInjection() {}
    public static void failAt(TagRecipeFaultPoint value) {
        if (FMLEnvironment.production) throw new IllegalStateException("fault injection is userdev-only");
        point = value;
    }
    public static void clear() { point = null; }
    public static void hit(TagRecipeFaultPoint value) {
        if (point == value) { point = null; throw new IllegalStateException("FAULT_INJECTED:" + value); }
    }
}
