package com.certitrack.model.entity;

import com.certitrack.dto.request.IssueCertificateRequest;
import com.certitrack.model.enums.CertificateStatus;
import com.certitrack.util.CertificateIds;
import com.certitrack.exception.CertificateAlreadyRevokedException;
import jakarta.persistence.*;
import java.time.*;
import java.util.Locale;

@Entity
@Table(name = "certificates")
public class Certificate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Temporarily null only inside the issuance transaction, until the primary key is known.
    @Column(unique = true, length = 31)
    private String certificateId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "student_id", nullable = false)
    private User student;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "issued_by_id", nullable = false)
    private User issuedBy;
    @Column(nullable = false, length = 12)
    private String programCode;
    @Column(nullable = false, length = 200)
    private String programName;
    @Column(nullable = false, length = 100)
    private String certificateType;
    @Column(nullable = false, length = 200)
    private String organizationName;
    @Column(nullable = false)
    private LocalDate issueDate;
    private LocalDate expiryDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private CertificateStatus status = CertificateStatus.VALID;
    @Column(length = 1000)
    private String revocationReason;
    private LocalDateTime revokedAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @Version
    private long version;

    protected Certificate() {}

    public Certificate(User student, User issuedBy, IssueCertificateRequest request, LocalDateTime now) {
        this.student = student;
        this.issuedBy = issuedBy;
        this.programCode = request.programCode().toUpperCase(Locale.ROOT);
        this.programName = request.programName().trim();
        this.certificateType = request.certificateType().trim();
        this.organizationName = request.organizationName().trim();
        this.issueDate = request.issueDate();
        this.expiryDate = request.expiryDate();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void assignCertificateId() {
        if (certificateId != null) throw new IllegalStateException("Certificate ID is immutable");
        certificateId = CertificateIds.generate(programCode, issueDate, id);
    }

    public void revoke(String reason, LocalDateTime now) {
        if (status == CertificateStatus.REVOKED) throw new CertificateAlreadyRevokedException();
        status = CertificateStatus.REVOKED;
        revocationReason = reason;
        revokedAt = now;
        updatedAt = now;
    }

    public Long getId() { return id; }
    public String getCertificateId() { return certificateId; }
    public User getStudent() { return student; }
    public User getIssuedBy() { return issuedBy; }
    public String getProgramCode() { return programCode; }
    public String getProgramName() { return programName; }
    public String getCertificateType() { return certificateType; }
    public String getOrganizationName() { return organizationName; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public CertificateStatus getStatus() { return status; }
    public CertificateStatus effectiveStatus(LocalDate today) {
        if (status == CertificateStatus.VALID && expiryDate != null && today.isAfter(expiryDate))
            return CertificateStatus.EXPIRED;
        return status;
    }
    public String getRevocationReason() { return revocationReason; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
