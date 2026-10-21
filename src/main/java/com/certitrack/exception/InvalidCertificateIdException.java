package com.certitrack.exception;

import org.springframework.http.HttpStatus;

public class InvalidCertificateIdException extends DomainException {
    public InvalidCertificateIdException() { super(HttpStatus.BAD_REQUEST, "Invalid certificate ID format"); }
}
