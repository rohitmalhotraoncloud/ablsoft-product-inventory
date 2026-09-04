package org.ablsoft.upwork.repository;

import lombok.RequiredArgsConstructor;
import org.ablsoft.upwork.model.Product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class ProductImportRepository {
    private static final int BATCH_SIZE = 500;
    private static final String INSERT_SQL = """
            insert into products (sku, name, category, purchase_date, unit_price, quantity)
            values (?, ?, ?, ?, ?, ?)
            """;
    private static final String FIND_EXISTING_SQL = """
            select sku, purchase_date
            from products
            where (sku, purchase_date) in (%s)
            """;

    private final JdbcTemplate jdbcTemplate;

    public Set<ProductKey> findExistingKeys(Set<ProductKey> keys) {
        if (keys.isEmpty()) {
            return Set.of();
        }

        List<ProductKey> keyList = List.copyOf(keys);
        Set<ProductKey> existing = new HashSet<>();
        for (int start = 0; start < keyList.size(); start += BATCH_SIZE) {
            List<ProductKey> batch = keyList.subList(start, Math.min(start + BATCH_SIZE, keyList.size()));
            String placeholders = String.join(", ", Collections.nCopies(batch.size(), "(?, ?)"));
            String sql = FIND_EXISTING_SQL.formatted(placeholders);
            existing.addAll(jdbcTemplate.query(sql, statement -> {
                int parameter = 1;
                for (ProductKey key : batch) {
                    statement.setString(parameter++, key.sku());
                    statement.setObject(parameter++, key.purchaseDate());
                }
            }, resultSet -> {
                Set<ProductKey> matches = new HashSet<>();
                while (resultSet.next()) {
                    Date purchaseDate = resultSet.getDate(2);
                    matches.add(new ProductKey(resultSet.getString(1), purchaseDate.toLocalDate()));
                }
                return matches;
            }));
        }
        return existing;
    }

    public void insertAll(List<Product> products) {
        jdbcTemplate.batchUpdate(INSERT_SQL, products, BATCH_SIZE, (statement, product) -> {
            statement.setString(1, product.getSku());
            statement.setString(2, product.getName());
            statement.setString(3, product.getCategory());
            statement.setObject(4, product.getPurchaseDate());
            statement.setBigDecimal(5, product.getUnitPrice());
            statement.setInt(6, product.getQuantity());
        });
    }

    public record ProductKey(String sku, LocalDate purchaseDate) {
    }
}
