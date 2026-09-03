package org.ablsoft.upwork.dto;

import org.ablsoft.upwork.model.Product;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record ProductDto(
        Long id,
        String productSku,
        String productName,
        String category,
        LocalDate purchaseDate,
        BigDecimal unitPrice,
        Integer quantity,
        long stockAgeDays
) {
    public static ProductDto from(Product product, Clock clock) {
        long age = ChronoUnit.DAYS.between(product.getPurchaseDate(), LocalDate.now(clock));
        return new ProductDto(product.getId(), product.getSku(), product.getName(),
                product.getCategory(), product.getPurchaseDate(), product.getUnitPrice(),
                product.getQuantity(), age);
    }
}
