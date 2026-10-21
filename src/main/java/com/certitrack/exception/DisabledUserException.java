package com.certitrack.exception;

import org.springframework.http.HttpStatus;

public class DisabledUserException extends DomainException {
    public DisabledUserException() { super(HttpStatus.FORBIDDEN, "Account is disabled"); }
}
