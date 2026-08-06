package com.gabriel0liv.partialreload.advancement;
import net.minecraftforge.fml.loading.FMLEnvironment;
import java.util.concurrent.atomic.AtomicReference;
public final class AdvancementFaultInjection {
 private static final AtomicReference<AdvancementFaultPoint> ARMED=new AtomicReference<>(); private AdvancementFaultInjection(){}
 public static void arm(AdvancementFaultPoint point){if(FMLEnvironment.production)throw new IllegalStateException("ADVANCEMENT_FAULT_INJECTION_NOT_AVAILABLE_IN_PRODUCTION");ARMED.set(point);}
 public static void clear(){ARMED.set(null);} public static void hit(AdvancementFaultPoint point){if(ARMED.compareAndSet(point,null))throw new IllegalStateException("ADVANCEMENT_FAULT_INJECTED:"+point);}
}
