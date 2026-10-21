package com.certitrack.service.impl;

import com.certitrack.dto.response.UserResponse;
import com.certitrack.dto.request.CertificateFilter;
import java.util.Locale;
import com.certitrack.model.entity.Certificate;
import com.certitrack.model.enums.CertificateStatus;
import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

public final class CertificateQueries {
    private CertificateQueries() {}

    public static Specification<Certificate> visibleTo(UserResponse user) {
        return (root, query, cb) -> switch (user.role()) {
            case ADMIN -> cb.conjunction();
            case ISSUER -> cb.equal(root.get("issuedBy").get("id"), user.id());
            case STUDENT -> cb.equal(root.get("student").get("id"), user.id());
        };
    }

    public static Specification<Certificate> status(CertificateStatus status, LocalDate today) {
        return (root, query, cb) -> switch (status) {
            case REVOKED -> cb.equal(root.get("status"), CertificateStatus.REVOKED);
            case EXPIRED -> cb.or(cb.equal(root.get("status"), CertificateStatus.EXPIRED),
                    cb.and(cb.equal(root.get("status"), CertificateStatus.VALID),
                            cb.lessThan(root.get("expiryDate"), today)));
            case VALID -> cb.and(cb.equal(root.get("status"), CertificateStatus.VALID),
                    cb.or(cb.isNull(root.get("expiryDate")), cb.greaterThanOrEqualTo(root.get("expiryDate"), today)));
        };
    }

    public static Specification<Certificate> filter(CertificateFilter filter, LocalDate today) {
        Specification<Certificate> spec = (root, query, cb) -> cb.conjunction();
        if (filter.status() != null) spec = spec.and(status(filter.status(), today));
        if (filter.certificateType() != null && !filter.certificateType().isBlank())
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("certificateType")),
                    filter.certificateType().trim().toLowerCase(Locale.ROOT)));
        if (filter.issueYear() != null)
            spec = spec.and((root, query, cb) -> cb.between(root.get("issueDate"),
                    LocalDate.of(filter.issueYear(), 1, 1), LocalDate.of(filter.issueYear(), 12, 31)));
        if (filter.search() != null && !filter.search().isBlank()) {
            String escaped = filter.search().trim().toLowerCase(Locale.ROOT)
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_");
            String pattern = "%" + escaped + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("certificateId")), pattern, '!'),
                    cb.like(cb.lower(root.get("student").get("fullName")), pattern, '!'),
                    cb.like(cb.lower(root.get("programName")), pattern, '!')));
        }
        return spec;
    }
}
