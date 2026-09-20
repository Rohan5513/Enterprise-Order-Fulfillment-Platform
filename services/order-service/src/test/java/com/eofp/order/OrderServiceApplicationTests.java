package com.eofp.order;

import org.junit.jupiter.api.Test;

class OrderServiceApplicationTests extends AbstractPostgresIntegrationTest {

    @Test
    void contextLoads() {
        // Starts the full application against real PostgreSQL: runs Flyway migrations and
        // lets Hibernate validate that the entities match the schema.
    }
}
