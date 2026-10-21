package com.certitrack;

import com.certitrack.config.SessionIdentity;
import com.certitrack.dto.request.IssueCertificateRequest;
import com.certitrack.dto.response.CertificateResponse;
import com.certitrack.model.enums.Role;
import com.certitrack.repository.CertificateRepository;
import com.certitrack.service.CertificateService;
import com.certitrack.util.CertificateIds;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CertificateIssuanceTest extends ApiTestSupport {
    @Autowired CertificateService certificates;
    @Autowired CertificateRepository repository;

    IssueCertificateRequest request(long student) {
        return new IssueCertificateRequest(student, "java", "Java Course", "Completion",
                "College", LocalDate.of(2026, 9, 24), null);
    }

    @Test
    void issuanceGeneratesAnImmutableIdFromTheDatabaseKey() throws Exception {
        var student = user(Role.STUDENT);
        var issuer = user(Role.ISSUER);
        Client client = login(issuer);
        var result = mvc.perform(post("/api/certificates").session(client.session())
                .header("X-CSRF-TOKEN", client.csrf()).contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(request(student.id()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.programCode").value("JAVA"))
                .andExpect(jsonPath("$.status").value("VALID")).andReturn();
        String id = body(result).get("certificateId").asText();
        var saved = repository.findByCertificateId(id).orElseThrow();
        assertThat(id).isEqualTo(CertificateIds.generate("JAVA", saved.getIssueDate(), saved.getId()));
        assertThat(saved.getExpiryDate()).isNull();
        assertThatThrownBy(saved::assignCertificateId).hasMessageContaining("immutable");
        mvc.perform(put("/api/certificates/" + id).session(client.session()).header("X-CSRF-TOKEN", client.csrf())
                .contentType(APPLICATION_JSON).content("{}")).andExpect(status().is4xxClientError());
    }

    @Test
    void onlyTheOwnerStudentAndIssuingUserCanReadPrivateDetails() throws Exception {
        var student = user(Role.STUDENT);
        var issuer = user(Role.ISSUER);
        String id = certificates.issue(new SessionIdentity(issuer.id(), issuer.role()), request(student.id())).certificateId();
        for (var allowed : List.of(student, issuer, user(Role.ADMIN))) {
            Client client = login(allowed);
            mvc.perform(get("/api/certificates/" + id).session(client.session()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.email").doesNotExist())
                    .andExpect(jsonPath("$.student").doesNotExist());
        }
        for (var forbidden : List.of(user(Role.STUDENT), user(Role.ISSUER))) {
            Client client = login(forbidden);
            mvc.perform(get("/api/certificates/" + id).session(client.session())).andExpect(status().isForbidden());
            mvc.perform(get("/api/certificates").session(client.session()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        }
        Client owner = login(student);
        mvc.perform(get("/api/student/certificates").session(owner.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/students").session(owner.session())).andExpect(status().isForbidden());
    }

    @Test
    void invalidIssuanceRollsBackAndStudentsCannotIssue() throws Exception {
        var issuer = user(Role.ISSUER);
        var student = user(Role.STUDENT);
        Client client = login(issuer);
        long before = repository.count();
        var invalid = new IssueCertificateRequest(student.id(), "JAVA", "Course", "Completion",
                "College", LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 23));
        mvc.perform(post("/api/certificates").session(client.session()).header("X-CSRF-TOKEN", client.csrf())
                .contentType(APPLICATION_JSON).content(json.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isEqualTo(before);
        assertThatThrownBy(() -> certificates.issue(new SessionIdentity(issuer.id(), issuer.role()), request(issuer.id())))
                .hasMessageContaining("not a student");
        Client owner = login(student);
        mvc.perform(post("/api/certificates").session(owner.session()).header("X-CSRF-TOKEN", owner.csrf())
                .contentType(APPLICATION_JSON).content(json.writeValueAsString(request(student.id()))))
                .andExpect(status().isForbidden());
        users.setEnabled(issuer.id(), student.id(), false);
        assertThatThrownBy(() -> certificates.issue(new SessionIdentity(issuer.id(), issuer.role()), request(student.id())))
                .hasMessageContaining("disabled");
    }

    @Test
    void concurrentIssuanceCreatesUniqueCompleteIds() throws Exception {
        var student = user(Role.STUDENT);
        var issuer = user(Role.ISSUER);
        var identity = new SessionIdentity(issuer.id(), issuer.role());
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<CertificateResponse>> jobs = new ArrayList<>();
            for (int i = 0; i < 12; i++) jobs.add(() -> certificates.issue(identity, request(student.id())));
            Set<String> ids = new HashSet<>();
            for (var future : pool.invokeAll(jobs)) {
                String id = future.get().certificateId();
                assertThat(id).matches("^CERT-JAVA-2026-[0-9]{5,8}$");
                ids.add(id);
            }
            assertThat(ids).hasSize(12);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void idSequenceBoundsAreExplicit() {
        assertThat(CertificateIds.generate("JAVA", LocalDate.of(2026, 1, 1), 142)).isEqualTo("CERT-JAVA-2026-00142");
        assertThat(CertificateIds.generate("AI", LocalDate.of(2026, 1, 1), 99_999_999)).endsWith("99999999");
        assertThatThrownBy(() -> CertificateIds.generate("AI", LocalDate.now(), 100_000_000))
                .isInstanceOf(IllegalStateException.class);
    }
}
