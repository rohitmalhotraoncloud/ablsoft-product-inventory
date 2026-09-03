package org.ablsoft.upwork.service;

import lombok.Getter;
import org.ablsoft.upwork.dto.RowError;
import org.ablsoft.upwork.exception.SpreadsheetValidationException;
import org.ablsoft.upwork.model.Product;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class ExcelReader {
    private static final String SKU_COLUMN = "Product SKU";
    private static final String NAME_COLUMN = "Product Name";
    private static final String CATEGORY_COLUMN = "Category";
    private static final String PURCHASE_DATE_COLUMN = "Purchase Date";
    private static final String UNIT_PRICE_COLUMN = "Unit Price";
    private static final String QUANTITY_COLUMN = "Quantity";
    private static final String XLS_EXTENSION = ".xls";
    private static final String XLSX_EXTENSION = ".xlsx";
    private static final BigDecimal MAX_UNIT_PRICE = new BigDecimal("99999999999999999.99");
    private static final List<String> HEADERS = List.of(
            SKU_COLUMN, NAME_COLUMN, CATEGORY_COLUMN, PURCHASE_DATE_COLUMN, UNIT_PRICE_COLUMN, QUANTITY_COLUMN);
    private static final DateTimeFormatter PURCHASE_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/uuuu");

    public List<ImportedProduct> read(MultipartFile file) {
        validateFile(file);

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            return readWorkbook(workbook);
        } catch (SpreadsheetValidationException exception) {
            throw exception;
        } catch (EncryptedDocumentException exception) {
            throw validation("Cannot read Excel file", new RowError(0, "file", "Password-protected files are not supported"));
        } catch (IOException | IllegalArgumentException exception) {
            throw validation("Cannot read Excel file", new RowError(0, "file", "The file is not a valid Excel workbook"));
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw validation("An Excel file is required", new RowError(0, "file", "File is empty"));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !hasExcelExtension(filename)) {
            throw validation("Invalid file format", new RowError(0, "file", "Only .xlsx and .xls files are supported"));
        }
    }

    private boolean hasExcelExtension(String filename) {
        String lowercaseFilename = filename.toLowerCase(Locale.ROOT);
        return lowercaseFilename.endsWith(XLSX_EXTENSION) || lowercaseFilename.endsWith(XLS_EXTENSION);
    }

    private List<ImportedProduct> readWorkbook(Workbook workbook) {
        if (workbook.getNumberOfSheets() == 0) {
            throw validation("Invalid Excel format", new RowError(1, "header", "Workbook has no sheets"));
        }

        Sheet sheet = workbook.getSheetAt(0);
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        return readSheet(sheet, evaluator);
    }

    private List<ImportedProduct> readSheet(Sheet sheet, FormulaEvaluator evaluator) {
        Map<String, Integer> columns = validateHeader(sheet.getRow(0), evaluator);
        List<ImportedProduct> products = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();

        for (int index = 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (isEmpty(row)) {
                continue;
            }
            int excelRow = index + 1;
            try {
                products.add(readRow(row, excelRow, columns, evaluator));
            } catch (RowValidationException exception) {
                errors.addAll(exception.getErrors());
            }
        }
        if (!errors.isEmpty()) {
            throw new SpreadsheetValidationException("Excel validation failed", errors);
        }
        if (products.isEmpty()) {
            throw validation("Excel validation failed", new RowError(2, "row", "No product data found"));
        }
        return products;
    }

    private Map<String, Integer> validateHeader(Row header, FormulaEvaluator evaluator) {
        if (header == null) {
            throw validation("Invalid Excel format", new RowError(1, "header", "Header row is missing"));
        }
        Map<String, Integer> found = new HashMap<>();
        List<RowError> errors = new ArrayList<>();
        for (Cell cell : header) {
            String value;
            try {
                value = text(cell, evaluator, 1, "header");
            } catch (RowValidationException exception) {
                continue;
            }
            for (String expected : HEADERS) {
                if (expected.equalsIgnoreCase(value.trim())) {
                    Integer previousColumn = found.putIfAbsent(expected, cell.getColumnIndex());
                    if (previousColumn != null) {
                        errors.add(new RowError(1, expected, "Column appears more than once"));
                    }
                }
            }
        }
        HEADERS.stream()
                .filter(headerName -> !found.containsKey(headerName))
                .map(headerName -> new RowError(1, headerName, "Required column is missing"))
                .forEach(errors::add);
        if (!errors.isEmpty()) {
            throw new SpreadsheetValidationException("Invalid Excel header", errors);
        }
        return found;
    }

    private ImportedProduct readRow(Row row, int excelRow, Map<String, Integer> columns, FormulaEvaluator evaluator) {
        List<RowError> errors = new ArrayList<>();
        String sku = capture(() -> text(cell(row, columns, SKU_COLUMN), evaluator, excelRow, SKU_COLUMN), errors);
        String name = capture(() -> text(cell(row, columns, NAME_COLUMN), evaluator, excelRow, NAME_COLUMN), errors);
        String category = capture(
                () -> text(cell(row, columns, CATEGORY_COLUMN), evaluator, excelRow, CATEGORY_COLUMN), errors);
        LocalDate purchaseDate = capture(
                () -> date(cell(row, columns, PURCHASE_DATE_COLUMN), evaluator, excelRow), errors);
        BigDecimal unitPrice = capture(
                () -> decimal(cell(row, columns, UNIT_PRICE_COLUMN), evaluator, excelRow), errors);
        Integer quantity = capture(
                () -> integer(cell(row, columns, QUANTITY_COLUMN), evaluator, excelRow), errors);
        if (unitPrice != null) {
            unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
        }

        validateMaximumLength(sku, 100, excelRow, SKU_COLUMN, errors);
        validateMaximumLength(name, 255, excelRow, NAME_COLUMN, errors);
        validateMaximumLength(category, 100, excelRow, CATEGORY_COLUMN, errors);
        validateNonNegative(unitPrice, excelRow, UNIT_PRICE_COLUMN, errors);
        validateMaximumUnitPrice(unitPrice, excelRow, errors);
        validateNonNegative(quantity, excelRow, QUANTITY_COLUMN, errors);

        if (!errors.isEmpty()) {
            throw new RowValidationException(errors);
        }
        return new ImportedProduct(excelRow, sku, name, category, purchaseDate, unitPrice, quantity);
    }

    private void validateMaximumLength(String value, int maximumLength, int row, String column,
                                       List<RowError> errors) {
        if (value != null && value.length() > maximumLength) {
            errors.add(new RowError(row, column, "Must not exceed " + maximumLength + " characters"));
        }
    }

    private void validateNonNegative(BigDecimal value, int row, String column, List<RowError> errors) {
        if (value != null && value.signum() < 0) {
            errors.add(new RowError(row, column, "Must be zero or greater"));
        }
    }

    private void validateMaximumUnitPrice(BigDecimal value, int row, List<RowError> errors) {
        if (value != null && value.compareTo(MAX_UNIT_PRICE) > 0) {
            errors.add(new RowError(row, UNIT_PRICE_COLUMN, "Value is too large"));
        }
    }

    private void validateNonNegative(Integer value, int row, String column, List<RowError> errors) {
        if (value != null && value < 0) {
            errors.add(new RowError(row, column, "Must be zero or greater"));
        }
    }

    private Cell cell(Row row, Map<String, Integer> columns, String name) {
        return row.getCell(columns.get(name), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    }

    private String text(Cell cell, FormulaEvaluator evaluator, int row, String column) {
        CellValue value = value(cell, evaluator, row, column);
        if (value.getCellType() != CellType.STRING || value.getStringValue().isBlank()) {
            throw error(row, column, "Must be non-empty text");
        }
        return value.getStringValue().trim();
    }

    private LocalDate date(Cell cell, FormulaEvaluator evaluator, int row) {
        CellValue value = value(cell, evaluator, row, PURCHASE_DATE_COLUMN);
        if (value.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)
                && DateUtil.isValidExcelDate(value.getNumberValue())) {
            return DateUtil.getLocalDateTime(value.getNumberValue()).toLocalDate();
        }
        if (value.getCellType() == CellType.STRING) {
            String dateText = value.getStringValue().trim();
            try {
                return LocalDate.parse(dateText, PURCHASE_DATE_FORMAT);
            } catch (DateTimeParseException ignored) {
                // Report the supported format below.
            }
        }
        throw error(row, PURCHASE_DATE_COLUMN, "Use MM/dd/yyyy or an Excel date");
    }

    private BigDecimal decimal(Cell cell, FormulaEvaluator evaluator, int row) {
        CellValue value = value(cell, evaluator, row, UNIT_PRICE_COLUMN);
        if (value.getCellType() != CellType.NUMERIC || !Double.isFinite(value.getNumberValue())) {
            throw error(row, UNIT_PRICE_COLUMN, "Must be a number or currency-formatted numeric cell");
        }
        return BigDecimal.valueOf(value.getNumberValue());
    }

    private Integer integer(Cell cell, FormulaEvaluator evaluator, int row) {
        CellValue value = value(cell, evaluator, row, QUANTITY_COLUMN);
        double number = value.getCellType() == CellType.NUMERIC ? value.getNumberValue() : Double.NaN;
        if (!Double.isFinite(number) || number != Math.rint(number) || number > Integer.MAX_VALUE || number < Integer.MIN_VALUE) {
            throw error(row, QUANTITY_COLUMN, "Must be a whole number");
        }
        return (int) number;
    }

    private CellValue value(Cell cell, FormulaEvaluator evaluator, int row, String column) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            throw error(row, column, "Value is required");
        }
        CellValue value = cell.getCellType() == CellType.FORMULA
                ? evaluateFormula(cell, evaluator, row, column)
                : switch (cell.getCellType()) {
            case STRING -> new CellValue(cell.getStringCellValue());
            case NUMERIC -> new CellValue(cell.getNumericCellValue());
            case BOOLEAN -> CellValue.valueOf(cell.getBooleanCellValue());
            case ERROR -> CellValue.getError(cell.getErrorCellValue());
            default -> null;
        };
        if (value == null || value.getCellType() == CellType.BLANK) {
            throw error(row, column, "Value is required");
        }
        if (value.getCellType() == CellType.ERROR) {
            throw error(row, column, "Formula error: " + FormulaError.forInt(value.getErrorValue()).getString());
        }
        return value;
    }

    private CellValue evaluateFormula(Cell cell, FormulaEvaluator evaluator, int row, String column) {
        try {
            return evaluator.evaluate(cell);
        } catch (RuntimeException exception) {
            throw error(row, column, "Formula could not be evaluated");
        }
    }

    private boolean isEmpty(Row row) {
        if (row == null) {
            return true;
        }
        for (Cell cell : row) {
            if (cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private <T> T capture(Supplier<T> supplier, List<RowError> errors) {
        try {
            return supplier.get();
        } catch (RowValidationException exception) {
            errors.addAll(exception.getErrors());
            return null;
        }
    }

    private RowValidationException error(int row, String column, String message) {
        return new RowValidationException(List.of(new RowError(row, column, message)));
    }

    private SpreadsheetValidationException validation(String message, RowError error) {
        return new SpreadsheetValidationException(message, List.of(error));
    }

    @Getter
    private static final class RowValidationException extends RuntimeException {
        private final List<RowError> errors;

        private RowValidationException(List<RowError> errors) {
            this.errors = List.copyOf(errors);
        }
    }

    public record ImportedProduct(int row, String sku, String name, String category,
                                  LocalDate purchaseDate, BigDecimal unitPrice, Integer quantity) {
        public Product toEntity() {
            return new Product(sku, name, category, purchaseDate, unitPrice, quantity);
        }
    }
}
