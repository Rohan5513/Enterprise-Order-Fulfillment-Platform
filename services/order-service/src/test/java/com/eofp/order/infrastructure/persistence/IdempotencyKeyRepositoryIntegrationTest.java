package com.eofp.order.infrastructure.persistence;

import com.eofp.order.AbstractPostgresIntegrationTest;
import com.eofp.order.infrastructure.persistence.IdempotencyKeyRepository.StoredKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyKeyRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String HASH = "a".repeat(64);

    @Autowired
    IdempotencyKeyRepository repository;

    @Test
    void aKeyCanOnlyBeClaimedOnce() {
        UUID customer = UUID.randomUUID();
        Instant expiry = Instant.now().plusSeconds(3600);

        assertTrue(repository.tryInsert(customer, "key-1", HASH, UUID.randomUUID(), expiry));
        assertFalse(repository.tryInsert(customer, "key-1", HASH, UUID.randomUUID(), expiry));
    }

    @Test
    void theStoredResponseIsFilledInByComplete() {
        UUID customer = UUID.randomUUID();
        UUID resource = UUID.randomUUID();
        repository.tryInsert(customer, "key-2", HASH, resource, Instant.now().plusSeconds(3600));

        StoredKey before = repository.find(customer, "key-2").orElseThrow();
        assertNull(before.responseStatus());
        assertNull(before.responseBody());

        repository.complete(customer, "key-2", 201, "{\"orderId\":\"x\"}");

        StoredKey after = repository.find(customer, "key-2").orElseThrow();
        assertEquals(HASH, after.requestHash());
        assertEquals(resource, after.resourceId());
        assertEquals(201, after.responseStatus());
        assertTrue(after.responseBody().contains("orderId"));
    }

    @Test
    void deleteExpiredRemovesOnlyExpiredKeys() {
        UUID customer = UUID.randomUUID();
        repository.tryInsert(customer, "old", HASH, UUID.randomUUID(), Instant.now().minusSeconds(3600));
        repository.tryInsert(customer, "fresh", HASH, UUID.randomUUID(), Instant.now().plusSeconds(3600));

        int deleted = repository.deleteExpired(Instant.now());

        assertTrue(deleted >= 1);
        assertTrue(repository.find(customer, "old").isEmpty());
        assertTrue(repository.find(customer, "fresh").isPresent());
    }
}
