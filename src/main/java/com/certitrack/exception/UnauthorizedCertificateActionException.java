package com.certitrack.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedCertificateActionException extends DomainException {
    public UnauthorizedCertificateActionException() { super(HttpStatus.FORBIDDEN, "You cannot access or modify this certificate"); }
}
