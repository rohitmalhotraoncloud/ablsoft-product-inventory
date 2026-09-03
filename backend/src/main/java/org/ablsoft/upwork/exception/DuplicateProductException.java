package org.ablsoft.upwork.exception;

import lombok.Getter;
import org.ablsoft.upwork.dto.RowError;

import java.util.List;

@Getter
public class DuplicateProductException extends RuntimeException {
    private final List<RowError> errors;

    public DuplicateProductException(List<RowError> errors) {
        super("One or more products have a duplicate Product SKU and Purchase Date");
        this.errors = List.copyOf(errors);
    }
}