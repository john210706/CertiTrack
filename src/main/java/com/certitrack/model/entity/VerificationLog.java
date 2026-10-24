package com.certitrack.model.entity;

import com.certitrack.model.enums.VerificationResult;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_logs")
public class VerificationLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String searchedCertificateId;
    private Long matchedCertificateDatabaseId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private VerificationResult result;
    @Column(nullable = false)
    private LocalDateTime verifiedAt;

    protected VerificationLog() {}

    public VerificationLog(String searchedCertificateId, Long matchedCertificateDatabaseId,
                           VerificationResult result, LocalDateTime verifiedAt) {
        this.searchedCertificateId = searchedCertificateId;
        this.matchedCertificateDatabaseId = matchedCertificateDatabaseId;
        this.result = result;
        this.verifiedAt = verifiedAt;
    }

    public Long getId() { return id; }
    public String getSearchedCertificateId() { return searchedCertificateId; }
    public Long getMatchedCertificateDatabaseId() { return matchedCertificateDatabaseId; }
    public VerificationResult getResult() { return result; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
}
