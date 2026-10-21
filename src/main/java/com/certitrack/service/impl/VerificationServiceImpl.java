package com.certitrack.service.impl;

import com.certitrack.dto.response.*;
import com.certitrack.model.entity.*;
import com.certitrack.model.enums.VerificationResult;
import com.certitrack.repository.*;
import com.certitrack.service.VerificationService;
import com.certitrack.util.CertificateIds;
import com.certitrack.util.PageRequests;
import java.time.*;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationServiceImpl implements VerificationService {
    private final CertificateRepository certificates;
    private final VerificationLogRepository logs;
    private final Clock clock;

    public VerificationServiceImpl(CertificateRepository certificates, VerificationLogRepository logs, Clock clock) {
        this.certificates = certificates;
        this.logs = logs;
        this.clock = clock;
    }

    @Override @Transactional
    public VerificationResponse verify(String certificateId) {
        String searchedId = certificateId == null ? "" : certificateId;
        Certificate certificate = null;
        VerificationResult result;
        if (!CertificateIds.isValid(searchedId)) {
            result = VerificationResult.INVALID_FORMAT;
        } else {
            certificate = certificates.findByCertificateId(searchedId).orElse(null);
            result = certificate == null ? VerificationResult.NOT_FOUND
                    : VerificationResult.valueOf(certificate.effectiveStatus(LocalDate.now(clock)).name());
        }
        // Return only after the same transaction has committed the audit record, including misses.
        logs.save(new VerificationLog(searchedId, certificate == null ? null : certificate.getId(),
                result, LocalDateTime.now(clock)));
        return VerificationResponse.from(searchedId, certificate, result);
    }

    @Override @Transactional(readOnly = true)
    public PageResponse<VerificationLogResponse> logs(Pageable pageable) {
        return PageResponse.from(logs.findAll(PageRequests.safe(pageable)).map(VerificationLogResponse::from));
    }
}
