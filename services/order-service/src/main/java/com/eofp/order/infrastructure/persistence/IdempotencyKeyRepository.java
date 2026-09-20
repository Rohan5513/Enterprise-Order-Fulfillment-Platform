package com.eofp.order.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Access to the idempotency_keys table (docs/10 section 2). Plain SQL on purpose: the whole
 * mechanism rests on one statement, INSERT ... ON CONFLICT DO NOTHING, and the unique constraint
 * behind it. JdbcTemplate joins the surrounding transaction, so the key row and the order are
 * committed (or rolled back) together.
 */
@Repository
public class IdempotencyKeyRepository {

    public record StoredKey(String requestHash, UUID resourceId, Integer responseStatus, String responseBody) {
    }

    private final JdbcTemplate jdbcTemplate;

    public IdempotencyKeyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Claims the key. Returns false if it already exists. If another transaction is inserting the
     * same key right now, this call waits until that transaction commits or rolls back.
     */
    public boolean tryInsert(UUID customerId, String key, String requestHash, UUID resourceId, Instant expiresAt) {
        int rows = jdbcTemplate.update("""
                INSERT INTO idempotency_keys (id, customer_id, idempotency_key, request_hash, resource_id, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (customer_id, idempotency_key) DO NOTHING
                """,
                UUID.randomUUID(), customerId, key, requestHash, resourceId,
                OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        return rows == 1;
    }

    public Optional<StoredKey> find(UUID customerId, String key) {
        return jdbcTemplate.query("""
                SELECT request_hash, resource_id, response_status, response_body::text AS response_body
                FROM idempotency_keys
                WHERE customer_id = ? AND idempotency_key = ?
                """,
                IdempotencyKeyRepository::mapRow, customerId, key).stream().findFirst();
    }

    /** Stores the response so a retry can be answered without redoing the work. */
    public void complete(UUID customerId, String key, int responseStatus, String responseBodyJson) {
        jdbcTemplate.update("""
                UPDATE idempotency_keys
                SET response_status = ?, response_body = CAST(? AS jsonb)
                WHERE customer_id = ? AND idempotency_key = ?
                """,
                responseStatus, responseBodyJson, customerId, key);
    }

    public int deleteExpired(Instant now) {
        return jdbcTemplate.update("DELETE FROM idempotency_keys WHERE expires_at < ?",
                OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
    }

    private static StoredKey mapRow(ResultSet rs, int rowNum) throws SQLException {
        int status = rs.getInt("response_status");
        Integer responseStatus = rs.wasNull() ? null : status;
        return new StoredKey(
                rs.getString("request_hash"),
                rs.getObject("resource_id", UUID.class),
                responseStatus,
                rs.getString("response_body"));
    }
}
