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

    @Test
    void sequencePreservesOrderAndNonMatchingPoints() {
        TagRecipeFaultInjection.clear();
        TagRecipeFaultInjection.armSequence(java.util.List.of(TagRecipeFaultPoint.AFTER_RECIPE_PUBLICATION, TagRecipeFaultPoint.DURING_ROLLBACK));
        assertEquals(java.util.List.of(TagRecipeFaultPoint.AFTER_RECIPE_PUBLICATION, TagRecipeFaultPoint.DURING_ROLLBACK), TagRecipeFaultInjection.pending());
        assertDoesNotThrow(() -> TagRecipeFaultInjection.hit(TagRecipeFaultPoint.BEFORE_VERIFICATION));
        assertEquals(2, TagRecipeFaultInjection.pending().size());
        assertThrows(IllegalStateException.class, () -> TagRecipeFaultInjection.hit(TagRecipeFaultPoint.AFTER_RECIPE_PUBLICATION));
        assertEquals(java.util.List.of(TagRecipeFaultPoint.DURING_ROLLBACK), TagRecipeFaultInjection.pending());
        TagRecipeFaultInjection.clear();
    }
}
