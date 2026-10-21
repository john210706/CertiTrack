package com.certitrack.dto.response;

import java.time.Instant;
import org.springframework.http.HttpStatus;

public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path);
    }
}
