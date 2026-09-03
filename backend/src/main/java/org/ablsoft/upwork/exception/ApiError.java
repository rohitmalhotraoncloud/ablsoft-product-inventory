package org.ablsoft.upwork.exception;

import org.ablsoft.upwork.dto.RowError;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<RowError> details
) {
}
