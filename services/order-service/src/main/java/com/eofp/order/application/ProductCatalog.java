package com.eofp.order.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Port to the Product Service (docs/06 section 4.4). The only synchronous dependency of order
 * creation. Products that do not exist are simply missing from the result.
 */
public interface ProductCatalog {

    Map<UUID, ProductInfo> findByIds(Collection<UUID> productIds);
}
