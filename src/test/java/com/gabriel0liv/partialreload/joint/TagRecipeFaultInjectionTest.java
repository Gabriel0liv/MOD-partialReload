package com.gabriel0liv.partialreload.joint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TagRecipeFaultInjectionTest {
    @Test
    void armIsConsumedOnceAndCanBeCleared() {
        TagRecipeFaultInjection.clear();
        TagRecipeFaultInjection.failAt(TagRecipeFaultPoint.AFTER_FIRST_TAG_BIND);
        assertEquals(TagRecipeFaultPoint.AFTER_FIRST_TAG_BIND, TagRecipeFaultInjection.current().orElseThrow());
        assertThrows(IllegalStateException.class, () -> TagRecipeFaultInjection.hit(TagRecipeFaultPoint.AFTER_FIRST_TAG_BIND));
        assertTrue(TagRecipeFaultInjection.current().isEmpty());
        TagRecipeFaultInjection.failAt(TagRecipeFaultPoint.BEFORE_VERIFICATION);
        TagRecipeFaultInjection.clear();
        assertTrue(TagRecipeFaultInjection.current().isEmpty());
    }
}
