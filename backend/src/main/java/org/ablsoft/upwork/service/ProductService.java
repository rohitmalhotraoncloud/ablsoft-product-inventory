package org.ablsoft.upwork.service;

import lombok.RequiredArgsConstructor;
import org.ablsoft.upwork.dto.ImportResult;
import org.ablsoft.upwork.dto.InventorySummary;
import org.ablsoft.upwork.dto.PageResponse;
import org.ablsoft.upwork.dto.ProductDto;
import org.ablsoft.upwork.dto.RowError;
import org.ablsoft.upwork.exception.DuplicateProductException;
import org.ablsoft.upwork.repository.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@Service
@RequiredArgsConstructor
public class ProductService {
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "id", "id",
            "productSku", "sku",
            "productName", "name",
            "category", "category",
            "purchaseDate", "purchaseDate",
            "unitPrice", "unitPrice",
            "quantity", "quantity"
    );

    private final ProductRepository repository;
    private final ExcelReader reader;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<ProductDto> list(int page, int size, String sortBy, Sort.Direction direction) {
        String entityField = SORT_FIELDS.get(sortBy);
        if ("stockAgeDays".equals(sortBy)) {
            entityField = "purchaseDate";
            direction = direction.isAscending() ? Sort.Direction.DESC : Sort.Direction.ASC;
        }
        if (entityField == null) {
            throw new IllegalArgumentException("Unsupported sort field: " + sortBy);
        }

        Page<ProductDto> result = repository.findAll(PageRequest.of(page, size, Sort.by(direction, entityField)))
                .map(product -> ProductDto.from(product, clock));
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public InventorySummary summary() {
        List<LocalDate> purchaseDates = repository.findAllPurchaseDates();
        LocalDate today = LocalDate.now(clock);
        double averageAge = purchaseDates.stream()
                .mapToLong(purchaseDate -> ChronoUnit.DAYS.between(purchaseDate, today))
                .average()
                .orElse(0.0);
        BigDecimal totalValue = repository.calculateInventoryValue();
        if (totalValue == null) {
            totalValue = BigDecimal.ZERO;
        }

        return new InventorySummary(purchaseDates.size(),
                totalValue.setScale(2, RoundingMode.HALF_UP), averageAge);
    }

    @Transactional
    public ImportResult importExcel(MultipartFile file) {
        List<ExcelReader.ImportedProduct> rows = reader.read(file);
        List<RowError> duplicates = duplicateErrors(rows);
        if (!duplicates.isEmpty()) {
            throw new DuplicateProductException(duplicates);
        }

        try {
            repository.saveAllAndFlush(rows.stream().map(ExcelReader.ImportedProduct::toEntity).toList());
        } catch (DataIntegrityViolationException ignored) {
            throw new DuplicateProductException(List.of(new RowError(0, "Product SKU + Purchase Date",
                    "A matching product was imported concurrently")));
        }
        return new ImportResult(rows.size());
    }

    private List<RowError> duplicateErrors(List<ExcelReader.ImportedProduct> rows) {
        List<RowError> errors = new ArrayList<>();
        Set<ProductKey> seen = new HashSet<>();
        for (ExcelReader.ImportedProduct row : rows) {
            if (!seen.add(new ProductKey(row.sku(), row.purchaseDate()))) {
                errors.add(new RowError(row.row(), "Product SKU + Purchase Date", "Duplicate combination in file"));
            }
        }

        Set<String> skus = rows.stream()
                .map(ExcelReader.ImportedProduct::sku)
                .collect(toSet());
        Set<LocalDate> purchaseDates = rows.stream()
                .map(ExcelReader.ImportedProduct::purchaseDate)
                .collect(toSet());
        Set<ProductKey> existing = repository.findExistingKeys(skus, purchaseDates).stream()
                .map(product -> new ProductKey(product.getSku(), product.getPurchaseDate()))
                .collect(toSet());

        for (ExcelReader.ImportedProduct row : rows) {
            if (existing.contains(new ProductKey(row.sku(), row.purchaseDate()))) {
                errors.add(new RowError(row.row(), "Product SKU + Purchase Date", "Combination already exists"));
            }
        }
        return errors;
    }

    private record ProductKey(String sku, LocalDate purchaseDate) {
    }
}
