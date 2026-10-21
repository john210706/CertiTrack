package com.certitrack.exception;

import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends DomainException {
    public DuplicateEmailException() { super(HttpStatus.CONFLICT, "Email is already registered"); }
}
