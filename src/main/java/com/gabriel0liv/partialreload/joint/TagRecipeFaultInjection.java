package com.gabriel0liv.partialreload.joint;

import net.minecraftforge.fml.loading.FMLEnvironment;
import java.util.*;

/** Userdev-only deterministic fault seam; never active in production. */
public final class TagRecipeFaultInjection {
    private static final Deque<TagRecipeFaultPoint> points = new ArrayDeque<>();
    private TagRecipeFaultInjection() {}
    public static synchronized void failAt(TagRecipeFaultPoint value) {
        if (FMLEnvironment.production) throw new IllegalStateException("fault injection is userdev-only");
        points.addLast(value);
    }
    public static synchronized void armSequence(List<TagRecipeFaultPoint> values) {
        if (FMLEnvironment.production) throw new IllegalStateException("fault injection is userdev-only");
        points.addAll(values);
    }
    public static synchronized void clear() { points.clear(); }
    public static synchronized List<TagRecipeFaultPoint> pending() { return List.copyOf(points); }
    public static synchronized Optional<TagRecipeFaultPoint> current() { return points.peekFirst() == null ? Optional.empty() : Optional.of(points.peekFirst()); }
    public static synchronized void hit(TagRecipeFaultPoint value) {
        if (value == points.peekFirst()) { points.removeFirst(); throw new IllegalStateException("FAULT_INJECTED:" + value); }
    }
}
