package com.certitrack.service.impl;

import com.certitrack.config.SessionIdentity;
import com.certitrack.dto.response.DashboardResponse;
import com.certitrack.model.enums.*;
import com.certitrack.repository.*;
import com.certitrack.service.*;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {
    private final UserService users;
    private final UserRepository userRepository;
    private final CertificateRepository certificates;
    private final VerificationLogRepository logs;
    private final Clock clock;

    public DashboardServiceImpl(UserService users, UserRepository userRepository, CertificateRepository certificates,
                                VerificationLogRepository logs, Clock clock) {
        this.users = users;
        this.userRepository = userRepository;
        this.certificates = certificates;
        this.logs = logs;
        this.clock = clock;
    }

    @Override
    public DashboardResponse summary(SessionIdentity identity) {
        var user = users.requireEnabled(identity.id());
        var scope = CertificateQueries.visibleTo(user);
        LocalDate today = LocalDate.now(clock);
        boolean admin = user.role() == Role.ADMIN;
        return new DashboardResponse(certificates.count(scope),
                certificates.count(scope.and(CertificateQueries.status(CertificateStatus.VALID, today))),
                certificates.count(scope.and(CertificateQueries.status(CertificateStatus.EXPIRED, today))),
                certificates.count(scope.and(CertificateQueries.status(CertificateStatus.REVOKED, today))),
                admin ? userRepository.countByRole(Role.STUDENT) : null,
                admin ? userRepository.countByRole(Role.ISSUER) : null,
                admin ? logs.countByVerifiedAtGreaterThanEqualAndVerifiedAtLessThan(
                        today.atStartOfDay(), today.plusDays(1).atStartOfDay()) : null);
    }
}
