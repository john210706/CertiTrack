package com.certitrack;

import com.certitrack.config.SessionIdentity;
import com.certitrack.dto.request.IssueCertificateRequest;
import com.certitrack.repository.*;
import com.certitrack.service.CertificateService;
import com.certitrack.model.enums.*;
import com.certitrack.util.CertificateIds;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.*;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificationTest extends ApiTestSupport {
    @Autowired CertificateService certificates;
    @Autowired VerificationLogRepository logs;
    @Autowired CertificateRepository repository;
    @Autowired Clock clock;

    String issue(LocalDate expiry) {
        var issuer = user(Role.ISSUER);
        var student = user(Role.STUDENT);
        return certificates.issue(new SessionIdentity(issuer.id(), issuer.role()),
                new IssueCertificateRequest(student.id(), "JAVA", "Java", "Completion", "College",
                        LocalDate.now(clock).minusDays(10), expiry)).certificateId();
    }

    @Test
    void publicResponseHasOnlyAllowlistedFieldsAndMatchedAttemptIsLogged() throws Exception {
        String id = issue(null);
        long before = logs.count();
        var result = mvc.perform(get("/api/public/verify/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID")).andReturn();
        var fields = new HashSet<String>();
        body(result).fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("certificateId", "holderName", "programName", "certificateType",
                "organizationName", "issueDate", "expiryDate", "status");
        assertThat(logs.count()).isEqualTo(before + 1);
        var log = logs.findAll(PageRequest.of(0, 1, Sort.by("id").descending())).getContent().get(0);
        assertThat(log.getMatchedCertificateDatabaseId()).isEqualTo(repository.findByCertificateId(id).orElseThrow().getId());
        assertThat(log.getResult()).isEqualTo(VerificationResult.VALID);
    }

    @Test
    void invalidAndMissingIdsAreDistinctAndBothAreLogged() throws Exception {
        for (var entry : Map.of("bad-id", "INVALID_FORMAT", "CERT-JAVA-2026-99999999", "NOT_FOUND").entrySet()) {
            long before = logs.count();
            mvc.perform(get("/api/public/verify/" + entry.getKey())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(entry.getValue()));
            assertThat(logs.count()).isEqualTo(before + 1);
            var log = logs.findAll(PageRequest.of(0, 1, Sort.by("id").descending())).getContent().get(0);
            assertThat(log.getSearchedCertificateId()).isEqualTo(entry.getKey());
            assertThat(log.getMatchedCertificateDatabaseId()).isNull();
        }
        mvc.perform(get("/api/public/verify/")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVALID_FORMAT"));
    }

    @Test
    void verificationUsesCurrentDateEvenBeforeTheSchedulerRuns() throws Exception {
        mvc.perform(get("/api/public/verify/" + issue(LocalDate.now(clock).minusDays(1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED"));
        mvc.perform(get("/api/public/verify/" + issue(LocalDate.now(clock))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALID"));
    }

    @Test
    void logsAreAdminOnlyAndErrorsHaveAConsistentSafeShape() throws Exception {
        Client issuer = login(user(Role.ISSUER));
        mvc.perform(get("/api/admin/verification-logs").session(issuer.session())).andExpect(status().isForbidden());
        Client admin = login(user(Role.ADMIN));
        mvc.perform(get("/api/admin/verification-logs").session(admin.session())).andExpect(status().isOk());
        mvc.perform(get("/api/certificates/bad").session(admin.session()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/certificates/bad"))
                .andExpect(jsonPath("$.trace").doesNotExist());
        mvc.perform(get("/api/certificates/CERT-JAVA-2026-99999999").session(admin.session()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/users").session(admin.session()).header("X-CSRF-TOKEN", admin.csrf())
                .contentType(APPLICATION_JSON).content("{invalid")).andExpect(status().isBadRequest());
    }

    @Test
    void regexRejectsCaseWhitespaceAndInvalidWidths() {
        assertThat(CertificateIds.isValid("CERT-AI-2026-00001")).isTrue();
        for (String id : List.of("cert-AI-2026-00001", "CERT-A-2026-00001", "CERT-AI-26-00001",
                "CERT-AI-2026-001", "CERT-AI-2026-123456789", "CERT-AI-2026-00001\n", " CERT-AI-2026-00001")) {
            assertThat(CertificateIds.isValid(id)).as(id).isFalse();
        }
    }

    @Test
    void listSortingCannotTraversePrivateFields() throws Exception {
        Client admin = login(user(Role.ADMIN));
        for (String path : List.of("/api/certificates?sort=student.password", "/api/admin/users?sort=password",
                "/api/students?sort=password", "/api/admin/verification-logs?sort=unknown")) {
            mvc.perform(get(path).session(admin.session())).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/certificates?size=10000").session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
    }
}
