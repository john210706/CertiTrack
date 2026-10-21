package com.certitrack.exception;

import org.springframework.http.HttpStatus;

public class InvalidExpiryDateException extends DomainException {
    public InvalidExpiryDateException() { super(HttpStatus.BAD_REQUEST, "Expiry date cannot be before issue date"); }
}
