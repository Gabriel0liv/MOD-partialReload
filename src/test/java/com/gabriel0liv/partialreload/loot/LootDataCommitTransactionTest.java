package com.gabriel0liv.partialreload.loot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import com.google.common.collect.ImmutableMultimap;

import static org.junit.jupiter.api.Assertions.*;

class LootDataCommitTransactionTest {
    @AfterEach
    void clearFault() {
        LootDataFaultInjection.clear();
    }

    @Test
    void transactionRetainsIdentityPreflightAndFailureEvidence() {
        UUID id = UUID.randomUUID();
        UUID preparation = UUID.randomUUID();
        LootDataCommitTransaction tx = new LootDataCommitTransaction(id, preparation, Instant.EPOCH,
                "test", 12, "active", generation());
        tx.status(LootDataTransactionStatus.READY);
        tx.elementsPublished(true);
        tx.typeIndexPublished(true);
        tx.failure("boom");
        assertEquals(id, tx.transactionId());
        assertEquals(preparation, tx.preparationId());
        assertEquals(12, tx.managerIdentity());
        assertEquals("active", tx.expectedActiveFingerprint());
        assertTrue(tx.expectedActiveGeneration().elements().isEmpty());
        assertEquals(LootDataTransactionStatus.READY, tx.status());
        assertTrue(tx.elementsPublished());
        assertTrue(tx.typeIndexPublished());
        assertEquals("boom", tx.failure());
        assertEquals(java.util.List.of("READY"), tx.events());
    }

    @Test
    void allRequiredTransactionStatesAreRepresented() {
        assertArrayEquals(new LootDataTransactionStatus[]{
                LootDataTransactionStatus.PREPARING,
                LootDataTransactionStatus.READY,
                LootDataTransactionStatus.COMMITTING,
                LootDataTransactionStatus.VERIFYING,
                LootDataTransactionStatus.SUCCESS,
                LootDataTransactionStatus.ROLLING_BACK,
                LootDataTransactionStatus.ROLLED_BACK,
                LootDataTransactionStatus.FAILED,
                LootDataTransactionStatus.DEGRADED
        }, LootDataTransactionStatus.values());
    }

    @Test
    void faultInjectionIsOneShot() {
        LootDataFaultInjection.arm(LootDataFaultPoint.AFTER_ELEMENTS_PUBLICATION);
        assertDoesNotThrow(() -> LootDataFaultInjection.hit(LootDataFaultPoint.BEFORE_PUBLICATION));
        assertThrows(IllegalStateException.class,
                () -> LootDataFaultInjection.hit(LootDataFaultPoint.AFTER_ELEMENTS_PUBLICATION));
        assertDoesNotThrow(() -> LootDataFaultInjection.hit(LootDataFaultPoint.AFTER_ELEMENTS_PUBLICATION));
    }

    @Test
    void allRequiredFaultPointsAreRepresented() {
        assertArrayEquals(new LootDataFaultPoint[]{
                LootDataFaultPoint.BEFORE_PUBLICATION,
                LootDataFaultPoint.AFTER_ELEMENTS_PUBLICATION,
                LootDataFaultPoint.AFTER_TYPE_INDEX_PUBLICATION,
                LootDataFaultPoint.DURING_VERIFICATION,
                LootDataFaultPoint.DURING_ROLLBACK
        }, LootDataFaultPoint.values());
    }

    @Test
    void statusJournalPreservesCommitRollbackOrder() {
        LootDataCommitTransaction tx = transaction();
        tx.status(LootDataTransactionStatus.READY);
        tx.status(LootDataTransactionStatus.COMMITTING);
        tx.status(LootDataTransactionStatus.VERIFYING);
        tx.status(LootDataTransactionStatus.ROLLING_BACK);
        tx.status(LootDataTransactionStatus.ROLLED_BACK);
        assertEquals(java.util.List.of("READY", "COMMITTING", "VERIFYING", "ROLLING_BACK", "ROLLED_BACK"),
                tx.events());
    }

    @Test
    void rollbackAndVerificationEvidenceAreIndependent() {
        LootDataCommitTransaction tx = transaction();
        tx.rollbackPerformed(true);
        assertTrue(tx.rollbackPerformed());
        assertFalse(tx.verificationPassed());
        tx.verificationPassed(true);
        assertTrue(tx.verificationPassed());
    }

    @Test
    void eventViewIsImmutable() {
        LootDataCommitTransaction tx = transaction();
        tx.status(LootDataTransactionStatus.READY);
        assertThrows(UnsupportedOperationException.class,
                () -> tx.events().add("INVALID"));
    }

    @Test
    void clearDisarmsPendingFault() {
        LootDataFaultInjection.arm(LootDataFaultPoint.BEFORE_PUBLICATION);
        LootDataFaultInjection.clear();
        assertDoesNotThrow(() -> LootDataFaultInjection.hit(LootDataFaultPoint.BEFORE_PUBLICATION));
    }

    private static LootDataCommitTransaction transaction() {
        return new LootDataCommitTransaction(UUID.randomUUID(), UUID.randomUUID(), Instant.EPOCH,
                "test", 42, "fingerprint", generation());
    }

    private static ActiveLootDataGeneration generation() {
        return new ActiveLootDataGeneration(
                Map.of(),
                ImmutableMultimap.of(), UUID.randomUUID(), "fingerprint");
    }
}
