package com.certitrack.service.impl;

import com.certitrack.config.SessionIdentity;
import com.certitrack.dto.request.IssueCertificateRequest;
import com.certitrack.dto.request.RevokeCertificateRequest;
import com.certitrack.dto.request.CertificateFilter;
import java.time.*;
import com.certitrack.dto.response.*;
import com.certitrack.exception.*;
import com.certitrack.util.CertificateIds;
import com.certitrack.util.PageRequests;
import com.certitrack.model.entity.*;
import com.certitrack.model.enums.Role;
import com.certitrack.repository.*;
import com.certitrack.service.CertificateService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import static org.springframework.http.HttpStatus.*;

@Service
@Validated
@Transactional(readOnly = true)
public class CertificateServiceImpl implements CertificateService {
    private final CertificateRepository certificates;
    private final UserRepository users;
    private final Clock clock;

    public CertificateServiceImpl(CertificateRepository certificates, UserRepository users, Clock clock) {
        this.certificates = certificates;
        this.users = users;
        this.clock = clock;
    }

    @Override @Transactional
    public CertificateResponse issue(SessionIdentity identity, IssueCertificateRequest request) {
        User issuer = requireIssuer(identity);
        User student = findStudent(request.studentId());
        if (!student.isEnabled()) throw new DomainException(BAD_REQUEST, "Student account is disabled");
        if (request.expiryDate() != null && request.expiryDate().isBefore(request.issueDate()))
            throw new InvalidExpiryDateException();
        if (request.issueDate().getYear() < 1000 || request.issueDate().getYear() > 9999
                || request.expiryDate() != null && request.expiryDate().getYear() > 9999)
            throw new DomainException(BAD_REQUEST, "Dates must use years between 1000 and 9999");
        Certificate certificate = certificates.saveAndFlush(
                new Certificate(student, issuer, request, LocalDateTime.now(clock)));
        certificate.assignCertificateId();
        certificates.flush();
        return CertificateResponse.from(certificate, LocalDate.now(clock));
    }

    @Override
    public CertificateResponse get(SessionIdentity identity, String certificateId) {
        User actor = actor(identity);
        Certificate certificate = find(certificateId);
        if (actor.getRole() == Role.ISSUER && !certificate.getIssuedBy().getId().equals(actor.getId())
                || actor.getRole() == Role.STUDENT && !certificate.getStudent().getId().equals(actor.getId()))
            throw new UnauthorizedCertificateActionException();
        return CertificateResponse.from(certificate, LocalDate.now(clock));
    }

    @Override @Transactional
    public CertificateResponse revoke(SessionIdentity identity, String certificateId, RevokeCertificateRequest request) {
        User actor = requireIssuer(identity);
        if (!CertificateIds.isValid(certificateId)) throw new InvalidCertificateIdException();
        Certificate certificate = certificates.findForRevocation(certificateId)
                .orElseThrow(CertificateNotFoundException::new);
        if (actor.getRole() == Role.ISSUER && !certificate.getIssuedBy().getId().equals(actor.getId()))
            throw new UnauthorizedCertificateActionException();
        certificate.revoke(request.reason().trim(), LocalDateTime.now(clock));
        certificates.flush();
        return CertificateResponse.from(certificate, LocalDate.now(clock));
    }

    @Override
    public PageResponse<CertificateResponse> list(SessionIdentity identity, CertificateFilter filter, Pageable pageable) {
        User actor = actor(identity);
        pageable = PageRequests.safe(pageable);
        LocalDate today = LocalDate.now(clock);
        var query = CertificateQueries.visibleTo(UserResponse.from(actor)).and(CertificateQueries.filter(filter, today));
        Page<Certificate> page = certificates.findAll(query, pageable);
        return PageResponse.from(page.map(certificate -> CertificateResponse.from(certificate, today)));
    }

    @Override
    public PageResponse<StudentResponse> students(SessionIdentity identity, Pageable pageable) {
        requireIssuer(identity);
        return PageResponse.from(users.findByRole(Role.STUDENT, PageRequests.safe(pageable)).map(StudentResponse::from));
    }

    @Override
    public StudentResponse student(SessionIdentity identity, long id) {
        requireIssuer(identity);
        return StudentResponse.from(findStudent(id));
    }

    private User actor(SessionIdentity identity) {
        if (identity == null) throw new DomainException(UNAUTHORIZED, "Login required");
        User actor = users.findById(identity.id()).orElseThrow(() -> new DomainException(UNAUTHORIZED, "Login required"));
        if (!actor.isEnabled()) throw new DomainException(FORBIDDEN, "Account is disabled");
        return actor;
    }

    private User requireIssuer(SessionIdentity identity) {
        User actor = actor(identity);
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.ISSUER)
            throw new DomainException(FORBIDDEN, "Administrator or issuer role required");
        return actor;
    }

    private User findStudent(long id) {
        User student = users.findById(id).orElseThrow(() -> new DomainException(NOT_FOUND, "Student not found"));
        if (student.getRole() != Role.STUDENT) throw new DomainException(BAD_REQUEST, "Selected user is not a student");
        return student;
    }

    private Certificate find(String certificateId) {
        if (!CertificateIds.isValid(certificateId)) throw new InvalidCertificateIdException();
        return certificates.findByCertificateId(certificateId)
                .orElseThrow(CertificateNotFoundException::new);
    }
}
