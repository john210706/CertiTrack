package com.certitrack.config;

import com.certitrack.dto.request.*;
import com.certitrack.dto.response.UserResponse;
import com.certitrack.model.enums.Role;
import com.certitrack.repository.*;
import com.certitrack.service.*;
import java.time.*;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {
    public static final String PASSWORD = "Demo-password-42";
    private final UserRepository users;
    private final CertificateRepository certificates;
    private final UserService userService;
    private final CertificateService certificateService;
    private final ExpirationService expirationService;
    private final Clock clock;

    public DemoDataSeeder(UserRepository users, CertificateRepository certificates, UserService userService,
                          CertificateService certificateService, ExpirationService expirationService, Clock clock) {
        this.users = users;
        this.certificates = certificates;
        this.userService = userService;
        this.certificateService = certificateService;
        this.expirationService = expirationService;
        this.clock = clock;
    }

    @Override public void run(ApplicationArguments args) {
        user("Demo Administrator", "admin@certitrack.local", Role.ADMIN, null);
        UserResponse issuer = user("Demo Issuer", "issuer@certitrack.local", Role.ISSUER, null);
        UserResponse student = user("Demo Student", "student@certitrack.local", Role.STUDENT, "DEMO-001");
        if (certificates.existsByStudent_IdAndIssuedBy_Id(student.id(), issuer.id())) return;

        SessionIdentity identity = new SessionIdentity(issuer.id(), issuer.role());
        LocalDate today = LocalDate.now(clock);
        issue(identity, student.id(), "JAVA", "Java Programming", "Course Completion", today.minusMonths(2), null);
        issue(identity, student.id(), "WEB", "Web Development", "Achievement", today.minusYears(1), today.minusDays(1));
        var revoked = issue(identity, student.id(), "DBMS", "Database Systems", "Course Completion",
                today.minusMonths(4), today.plusMonths(8));
        certificateService.revoke(identity, revoked.certificateId(), new RevokeCertificateRequest("Demo revocation"));
        expirationService.expireCertificates();
    }

    private UserResponse user(String name, String email, Role role, String registration) {
        return users.findByEmail(email).map(UserResponse::from).orElseGet(() ->
                userService.create(new CreateUserRequest(name, email, PASSWORD, role, registration)));
    }

    private com.certitrack.dto.response.CertificateResponse issue(SessionIdentity identity, long studentId,
            String code, String name, String type, LocalDate issueDate, LocalDate expiryDate) {
        return certificateService.issue(identity, new IssueCertificateRequest(studentId, code, name, type,
                "CertiTrack College", issueDate, expiryDate));
    }
}
