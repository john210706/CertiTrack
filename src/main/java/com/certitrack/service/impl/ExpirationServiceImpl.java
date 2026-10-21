package com.certitrack.service.impl;

import com.certitrack.model.enums.CertificateStatus;
import com.certitrack.repository.CertificateRepository;
import com.certitrack.service.ExpirationService;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpirationServiceImpl implements ExpirationService {
    private final CertificateRepository certificates;
    private final Clock clock;

    public ExpirationServiceImpl(CertificateRepository certificates, Clock clock) {
        this.certificates = certificates;
        this.clock = clock;
    }

    @Override @Transactional
    public int expireCertificates() {
        LocalDateTime now = LocalDateTime.now(clock);
        return certificates.expireEligible(CertificateStatus.VALID, CertificateStatus.EXPIRED, now.toLocalDate(), now);
    }
}
