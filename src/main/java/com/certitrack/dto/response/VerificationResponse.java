package com.certitrack.dto.response;

import com.certitrack.model.entity.Certificate;
import com.certitrack.model.enums.VerificationResult;
import java.time.LocalDate;

public record VerificationResponse(String certificateId, String holderName, String programName,
        String certificateType, String organizationName, LocalDate issueDate, LocalDate expiryDate,
        VerificationResult status) {
    public static VerificationResponse from(String searchedId, Certificate certificate, VerificationResult result) {
        if (certificate == null) return new VerificationResponse(searchedId, null, null, null, null, null, null, result);
        return new VerificationResponse(certificate.getCertificateId(), certificate.getStudent().getFullName(),
                certificate.getProgramName(), certificate.getCertificateType(), certificate.getOrganizationName(),
                certificate.getIssueDate(), certificate.getExpiryDate(), result);
    }
}
