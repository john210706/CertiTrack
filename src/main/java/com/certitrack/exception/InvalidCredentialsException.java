package com.certitrack.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends DomainException {
    public InvalidCredentialsException() { super(HttpStatus.UNAUTHORIZED, "Invalid email or password"); }
}
