package org.ablsoft.upwork.repository;

import org.ablsoft.upwork.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            select count(product) as totalProducts,
                   sum(product.unitPrice * product.quantity) as totalInventoryValue,
                   avg(timestampdiff(day, product.purchaseDate, :today)) as averageStockAgeDays
            from Product product
            """)
    InventorySummaryProjection calculateInventorySummary(@Param("today") LocalDate today);

    interface InventorySummaryProjection {
        long getTotalProducts();

        BigDecimal getTotalInventoryValue();

        Double getAverageStockAgeDays();
    }
}
