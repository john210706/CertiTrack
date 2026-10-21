package com.certitrack.exception;

import org.springframework.http.HttpStatus;

public class CertificateNotFoundException extends DomainException {
    public CertificateNotFoundException() { super(HttpStatus.NOT_FOUND, "Certificate not found"); }
}
