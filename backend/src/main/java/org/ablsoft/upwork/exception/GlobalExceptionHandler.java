package org.ablsoft.upwork.exception;

import jakarta.validation.ConstraintViolationException;
import org.ablsoft.upwork.dto.RowError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(SpreadsheetValidationException.class)
    ResponseEntity<ApiError> spreadsheet(SpreadsheetValidationException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), exception.getErrors());
    }

    @ExceptionHandler(DuplicateProductException.class)
    ResponseEntity<ApiError> duplicate(DuplicateProductException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), exception.getErrors());
    }

    @ExceptionHandler({ConstraintViolationException.class, IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class})
    ResponseEntity<ApiError> badRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException ignored) {
        return response(HttpStatus.CONTENT_TOO_LARGE, "File exceeds the 10 MB upload limit", List.of());
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String message, List<RowError> details) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(),
                status.getReasonPhrase(), message, details));
    }
}
