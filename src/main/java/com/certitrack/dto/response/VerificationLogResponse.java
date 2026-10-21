package com.certitrack.dto.response;

import com.certitrack.model.entity.VerificationLog;
import com.certitrack.model.enums.VerificationResult;
import java.time.LocalDateTime;

public record VerificationLogResponse(Long id, String searchedCertificateId, Long matchedCertificateDatabaseId,
                                      VerificationResult result, LocalDateTime verifiedAt) {
    public static VerificationLogResponse from(VerificationLog log) {
        return new VerificationLogResponse(log.getId(), log.getSearchedCertificateId(),
                log.getMatchedCertificateDatabaseId(), log.getResult(), log.getVerifiedAt());
    }
}
