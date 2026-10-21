package com.certitrack;

import com.certitrack.config.SessionIdentity;
import com.certitrack.dto.request.*;
import com.certitrack.model.enums.Role;
import com.certitrack.service.CertificateService;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardTest extends ApiTestSupport {
    @Autowired CertificateService certificates;
    @Autowired Clock clock;

    @Test
    void dashboardCountsAreScopedAndUseEffectiveExpiry() throws Exception {
        var issuer = user(Role.ISSUER);
        var student = user(Role.STUDENT);
        var identity = new SessionIdentity(issuer.id(), issuer.role());
        var today = LocalDate.now(clock);
        certificates.issue(identity, new IssueCertificateRequest(student.id(), "JAVA", "Course", "Completion",
                "College", today.minusDays(3), null));
        certificates.issue(identity, new IssueCertificateRequest(student.id(), "JAVA", "Course", "Completion",
                "College", today.minusDays(3), today.minusDays(1)));
        var revoked = certificates.issue(identity, new IssueCertificateRequest(student.id(), "JAVA", "Course",
                "Completion", "College", today.minusDays(3), null));
        certificates.revoke(identity, revoked.certificateId(), new RevokeCertificateRequest("Withdrawn"));
        for (var account : java.util.List.of(issuer, student)) {
            Client client = login(account);
            mvc.perform(get("/api/dashboard").session(client.session())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCertificates").value(3))
                    .andExpect(jsonPath("$.validCertificates").value(1))
                    .andExpect(jsonPath("$.expiredCertificates").value(1))
                    .andExpect(jsonPath("$.revokedCertificates").value(1))
                    .andExpect(jsonPath("$.totalStudents").isEmpty());
        }
        Client other = login(user(Role.ISSUER));
        mvc.perform(get("/api/dashboard").session(other.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCertificates").value(0));
    }

    @Test
    void pagesAreServedWithSecurityHeadersAndRoleProtection() throws Exception {
        for (String path : java.util.List.of("/", "/login.html", "/verify.html", "/css/app.css", "/js/app.js")) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")));
        }
        mvc.perform(get("/admin/dashboard.html")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login.html"));
        Client student = login(user(Role.STUDENT));
        mvc.perform(get("/admin/users.html").session(student.session())).andExpect(status().isForbidden());
        mvc.perform(get("/student/dashboard.html").session(student.session())).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-page=\"dashboard\"")));
    }
}
