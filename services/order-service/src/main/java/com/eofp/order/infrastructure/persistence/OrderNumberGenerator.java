package com.eofp.order.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Year;

/**
 * Human-readable order numbers such as ORD-2026-00000001, built from a database sequence so two
 * concurrent requests can never receive the same number. Sequences may have gaps after rollbacks,
 * which is fine for a display number.
 */
@Component
public class OrderNumberGenerator {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public OrderNumberGenerator(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public String next() {
        Long sequence = jdbcTemplate.queryForObject("SELECT nextval('order_number_seq')", Long.class);
        if (sequence == null) {
            throw new IllegalStateException("order_number_seq returned no value");
        }
        return "ORD-%d-%08d".formatted(Year.now(clock).getValue(), sequence);
    }
}
