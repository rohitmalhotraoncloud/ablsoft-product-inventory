package org.ablsoft.upwork.exception;

import lombok.Getter;
import org.ablsoft.upwork.dto.RowError;

import java.util.List;

@Getter
public class SpreadsheetValidationException extends RuntimeException {
    private final List<RowError> errors;

    public SpreadsheetValidationException(String message, List<RowError> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }
}
