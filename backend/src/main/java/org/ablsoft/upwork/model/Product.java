package org.ablsoft.upwork.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "products", uniqueConstraints = @UniqueConstraint(
        name = "uq_products_sku_purchase_date",
        columnNames = {"sku", "purchase_date"}
))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    public Product(String sku, String name, String category, LocalDate purchaseDate,
                   BigDecimal unitPrice, Integer quantity) {
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.purchaseDate = purchaseDate;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }
}
