package com.eofp.order.infrastructure.persistence;

import com.eofp.order.domain.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    // Loads the items in the same query, so callers never trigger lazy loading outside a transaction
    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(UUID id);
}
