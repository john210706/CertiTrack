package com.certitrack.service.impl;

import com.certitrack.config.SessionIdentity;
import com.certitrack.dto.request.CertificateFilter;
import com.certitrack.exception.DomainException;
import com.certitrack.model.enums.Role;
import com.certitrack.repository.CertificateRepository;
import com.certitrack.service.*;
import com.certitrack.util.Csv;
import java.io.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class CsvExportServiceImpl implements CsvExportService {
    private final CertificateRepository certificates;
    private final UserService users;
    private final Clock clock;
    private final EntityManager entityManager;

    public CsvExportServiceImpl(CertificateRepository certificates, UserService users, Clock clock,
                                EntityManager entityManager) {
        this.certificates = certificates;
        this.users = users;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void export(SessionIdentity identity, CertificateFilter filter, Writer writer) throws IOException {
        var user = users.requireEnabled(identity.id());
        if (user.role() != Role.ADMIN) throw new DomainException(HttpStatus.FORBIDDEN, "Administrator role required");
        LocalDate today = LocalDate.now(clock);
        var query = CertificateQueries.visibleTo(user).and(CertificateQueries.filter(filter, today));
        Csv.row(writer, "certificateId", "studentName", "programCode", "programName", "certificateType",
                "organizationName", "issueDate", "expiryDate", "status");
        int number = 0;
        Page<com.certitrack.model.entity.Certificate> batch;
        do {
            batch = certificates.findAll(query, PageRequest.of(number++, 200, Sort.by("id")));
            for (var certificate : batch) {
                Csv.row(writer, certificate.getCertificateId(), certificate.getStudent().getFullName(),
                        certificate.getProgramCode(), certificate.getProgramName(), certificate.getCertificateType(),
                        certificate.getOrganizationName(), certificate.getIssueDate(), certificate.getExpiryDate(),
                        certificate.effectiveStatus(today));
            }
            writer.flush();
            entityManager.clear();
        } while (batch.hasNext());
    }
}
