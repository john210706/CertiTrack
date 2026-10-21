package com.certitrack;

import com.certitrack.config.SessionIdentity;
import com.certitrack.dto.request.*;
import com.certitrack.dto.response.UserResponse;
import com.certitrack.exception.CertificateAlreadyRevokedException;
import com.certitrack.model.enums.*;
import com.certitrack.repository.*;
import com.certitrack.service.*;
import java.time.*;
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
class CertificateLifecycleTest extends ApiTestSupport {
    @Autowired CertificateService certificates;
    @Autowired ExpirationService expiration;
    @Autowired CertificateRepository repository;
    @Autowired Clock clock;

    SessionIdentity identity(UserResponse user) { return new SessionIdentity(user.id(), user.role()); }

    String issue(UserResponse issuer, LocalDate expiry) {
        var student = user(Role.STUDENT);
        return certificates.issue(identity(issuer), new IssueCertificateRequest(student.id(), "JAVA",
                "Java", "Completion", "College", LocalDate.now(clock).minusDays(10), expiry)).certificateId();
    }

    @Test
    void issuerCanRevokeOwnCertificateAndRepeatIsRejected() throws Exception {
        var issuer = user(Role.ISSUER);
        String id = issue(issuer, null);
        Client client = login(issuer);
        mvc.perform(post("/api/certificates/" + id + "/revoke").session(client.session())
                .header("X-CSRF-TOKEN", client.csrf()).contentType(APPLICATION_JSON).content("{\"reason\":\" Withdrawn \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.revocationReason").value("Withdrawn"))
                .andExpect(jsonPath("$.revokedAt").isNotEmpty());
        mvc.perform(post("/api/certificates/" + id + "/revoke").session(client.session())
                .header("X-CSRF-TOKEN", client.csrf()).contentType(APPLICATION_JSON).content("{\"reason\":\"Again\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/public/verify/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.revocationReason").doesNotExist());
        assertThat(repository.findByCertificateId(id).orElseThrow().getRevocationReason()).isEqualTo("Withdrawn");
    }

    @Test
    void onlyAdminCanRevokeAnotherIssuersCertificateAndReasonIsRequired() throws Exception {
        String id = issue(user(Role.ISSUER), null);
        for (Role role : List.of(Role.ISSUER, Role.STUDENT)) {
            Client client = login(user(role));
            mvc.perform(post("/api/certificates/" + id + "/revoke").session(client.session())
                    .header("X-CSRF-TOKEN", client.csrf()).contentType(APPLICATION_JSON).content("{\"reason\":\"No\"}"))
                    .andExpect(status().isForbidden());
        }
        Client anonymous = anonymous();
        mvc.perform(post("/api/certificates/" + id + "/revoke").session(anonymous.session())
                .header("X-CSRF-TOKEN", anonymous.csrf()).contentType(APPLICATION_JSON).content("{\"reason\":\"No\"}"))
                .andExpect(status().isUnauthorized());
        Client admin = login(user(Role.ADMIN));
        mvc.perform(post("/api/certificates/" + id + "/revoke").session(admin.session())
                .header("X-CSRF-TOKEN", admin.csrf()).contentType(APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/certificates/" + id + "/revoke").session(admin.session())
                .header("X-CSRF-TOKEN", admin.csrf()).contentType(APPLICATION_JSON).content("{\"reason\":\"Admin decision\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void expiryOnlyUpdatesPastDueValidRowsAndKeepsRevokedPermanent() {
        var issuer = user(Role.ISSUER);
        LocalDate today = LocalDate.now(clock);
        String overdue = issue(issuer, today.minusDays(1));
        String dueToday = issue(issuer, today);
        String future = issue(issuer, today.plusDays(1));
        String noExpiry = issue(issuer, null);
        String revoked = issue(issuer, today.minusDays(1));
        certificates.revoke(identity(issuer), revoked, new RevokeCertificateRequest("Withdrawn"));
        assertThat(certificates.get(identity(issuer), overdue).status()).isEqualTo(CertificateStatus.EXPIRED);
        assertThat(expiration.expireCertificates()).isGreaterThanOrEqualTo(1);
        assertThat(repository.findByCertificateId(overdue).orElseThrow().getStatus()).isEqualTo(CertificateStatus.EXPIRED);
        for (String id : List.of(dueToday, future, noExpiry))
            assertThat(repository.findByCertificateId(id).orElseThrow().getStatus()).isEqualTo(CertificateStatus.VALID);
        assertThat(repository.findByCertificateId(revoked).orElseThrow().getStatus()).isEqualTo(CertificateStatus.REVOKED);
        assertThat(expiration.expireCertificates()).isZero();
        assertThat(certificates.revoke(identity(issuer), overdue, new RevokeCertificateRequest("Expired then withdrawn")).status())
                .isEqualTo(CertificateStatus.REVOKED);
    }

    @Test
    void concurrentRevocationsHaveOnlyOneWinner() throws Exception {
        var issuer = user(Role.ISSUER);
        String id = issue(issuer, null);
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> revoke = () -> {
            start.await();
            try {
                certificates.revoke(identity(issuer), id, new RevokeCertificateRequest("Once only"));
                return true;
            } catch (CertificateAlreadyRevokedException ex) {
                return false;
            }
        };
        try {
            var first = pool.submit(revoke);
            var second = pool.submit(revoke);
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void simultaneousExpiryAndRevocationNeverRestoreARevokedCertificate() throws Exception {
        var issuer = user(Role.ISSUER);
        String id = issue(issuer, LocalDate.now(clock).minusDays(1));
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var revoke = pool.submit(() -> {
                start.await();
                return certificates.revoke(identity(issuer), id, new RevokeCertificateRequest("Withdrawn"));
            });
            var expire = pool.submit(() -> {
                start.await();
                return expiration.expireCertificates();
            });
            start.countDown();
            revoke.get(10, TimeUnit.SECONDS);
            expire.get(10, TimeUnit.SECONDS);
            expiration.expireCertificates();
            assertThat(repository.findByCertificateId(id).orElseThrow().getStatus()).isEqualTo(CertificateStatus.REVOKED);
        } finally {
            pool.shutdownNow();
        }
    }
}
