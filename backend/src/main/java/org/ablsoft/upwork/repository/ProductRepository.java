package org.ablsoft.upwork.repository;

import org.ablsoft.upwork.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            select product.sku as sku, product.purchaseDate as purchaseDate
            from Product product
            where product.sku in :skus
              and product.purchaseDate in :purchaseDates
            """)
    List<ProductKeyProjection> findExistingKeys(
            @Param("skus") Collection<String> skus,
            @Param("purchaseDates") Collection<LocalDate> purchaseDates);

    @Query("select sum(product.unitPrice * product.quantity) from Product product")
    BigDecimal calculateInventoryValue();

    @Query("select product.purchaseDate from Product product")
    List<LocalDate> findAllPurchaseDates();

    interface ProductKeyProjection {
        String getSku();

        LocalDate getPurchaseDate();
    }
}
