package com.certitrack.exception;

import org.springframework.http.HttpStatus;

public class CertificateAlreadyRevokedException extends DomainException {
    public CertificateAlreadyRevokedException() { super(HttpStatus.CONFLICT, "Certificate is already revoked"); }
}
