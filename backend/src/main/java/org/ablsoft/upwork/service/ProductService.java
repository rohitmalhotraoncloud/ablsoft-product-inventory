package org.ablsoft.upwork.service;

import lombok.RequiredArgsConstructor;
import org.ablsoft.upwork.dto.ImportResult;
import org.ablsoft.upwork.dto.InventorySummary;
import org.ablsoft.upwork.dto.PageResponse;
import org.ablsoft.upwork.dto.ProductDto;
import org.ablsoft.upwork.dto.RowError;
import org.ablsoft.upwork.exception.DuplicateProductException;
import org.ablsoft.upwork.repository.ProductImportRepository;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.ablsoft.upwork.repository.ProductImportRepository.ProductKey;

@Service
@RequiredArgsConstructor
public class ProductService {
    private static final int CURRENCY_SCALE = 2;
    private static final String PRODUCT_KEY_FIELD = "Product SKU + Purchase Date";
    private static final String DUPLICATE_IN_FILE = "Duplicate combination in file";
    private static final String ALREADY_EXISTS = "Combination already exists";
    private static final String CONCURRENT_DUPLICATE = "A matching product was imported concurrently";
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
    private final ProductImportRepository importRepository;
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

        LocalDate today = LocalDate.now(clock);
        Page<ProductDto> result = repository.findAll(PageRequest.of(page, size, Sort.by(direction, entityField)))
                .map(product -> ProductDto.from(product, today));
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public InventorySummary summary() {
        ProductRepository.InventorySummaryProjection projection =
                repository.calculateInventorySummary(LocalDate.now(clock));
        BigDecimal totalValue = Objects.requireNonNullElse(
                projection.getTotalInventoryValue(), BigDecimal.ZERO);
        double averageAge = Objects.requireNonNullElse(
                projection.getAverageStockAgeDays(), 0.0);

        return new InventorySummary(projection.getTotalProducts(),
                totalValue.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP), averageAge);
    }

    @Transactional
    public ImportResult importExcel(MultipartFile file) {
        List<ExcelReader.ImportedProduct> rows = reader.read(file);
        List<RowError> duplicates = duplicateErrors(rows);
        if (!duplicates.isEmpty()) {
            throw new DuplicateProductException(duplicates);
        }

        try {
            importRepository.insertAll(rows.stream().map(ExcelReader.ImportedProduct::toEntity).toList());
        } catch (DataIntegrityViolationException ignored) {
            throw new DuplicateProductException(List.of(
                    new RowError(0, PRODUCT_KEY_FIELD, CONCURRENT_DUPLICATE)));
        }
        return new ImportResult(rows.size());
    }

    private List<RowError> duplicateErrors(List<ExcelReader.ImportedProduct> rows) {
        List<RowError> errors = new ArrayList<>();
        Set<ProductKey> importedKeys = new HashSet<>();
        for (ExcelReader.ImportedProduct row : rows) {
            if (!importedKeys.add(new ProductKey(row.sku(), row.purchaseDate()))) {
                errors.add(new RowError(row.row(), PRODUCT_KEY_FIELD, DUPLICATE_IN_FILE));
            }
        }
        if (!errors.isEmpty()) {
            return errors;
        }

        Set<ProductKey> existing = importRepository.findExistingKeys(importedKeys);

        for (ExcelReader.ImportedProduct row : rows) {
            if (existing.contains(new ProductKey(row.sku(), row.purchaseDate()))) {
                errors.add(new RowError(row.row(), PRODUCT_KEY_FIELD, ALREADY_EXISTS));
            }
        }
        return errors;
    }

}
