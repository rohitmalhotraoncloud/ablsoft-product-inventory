package org.ablsoft.upwork.dto;

import java.math.BigDecimal;

public record InventorySummary(
        long totalProducts,
        BigDecimal totalInventoryValue,
        double averageStockAgeDays
) {
}
