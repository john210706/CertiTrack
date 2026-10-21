package com.certitrack.dto.response;

import com.certitrack.model.entity.Certificate;
import com.certitrack.model.enums.CertificateStatus;
import java.time.*;

public record CertificateResponse(String certificateId, String holderName, String issuerName,
        String programCode, String programName, String certificateType, String organizationName,
        LocalDate issueDate, LocalDate expiryDate, CertificateStatus status,
        String revocationReason, LocalDateTime revokedAt) {
    public static CertificateResponse from(Certificate certificate, LocalDate today) {
        return new CertificateResponse(certificate.getCertificateId(), certificate.getStudent().getFullName(),
                certificate.getIssuedBy().getFullName(), certificate.getProgramCode(), certificate.getProgramName(),
                certificate.getCertificateType(), certificate.getOrganizationName(), certificate.getIssueDate(),
                certificate.getExpiryDate(), certificate.effectiveStatus(today), certificate.getRevocationReason(),
                certificate.getRevokedAt());
    }
}
