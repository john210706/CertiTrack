package com.certitrack;

import com.certitrack.model.enums.CertificateStatus;
import com.certitrack.repository.CertificateRepository;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:certitrack-http;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "BOOTSTRAP_ADMIN_NAME=HTTP Test Administrator",
        "BOOTSTRAP_ADMIN_EMAIL=bootstrap@example.test",
        "BOOTSTRAP_ADMIN_PASSWORD=Test-password-42",
        "certitrack.expiry.enabled=true",
        "certitrack.expiry.cron=*/1 * * * * *"
})
@ActiveProfiles({"test", "bootstrap-admin"})
class HttpLifecycleTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired CertificateRepository certificates;
    @Autowired Clock clock;
    final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

    @Test
    void actualHttpSessionBootstrapAndSchedulerCompleteTheLifecycle() throws Exception {
        var csrf = get("/api/auth/csrf").get("token").asText();
        post("/api/auth/login", csrf, Map.of("email", "bootstrap@example.test",
                "password", "Test-password-42"), 200);
        csrf = get("/api/auth/csrf").get("token").asText();
        var student = post("/api/admin/users", csrf, Map.of("fullName", "HTTP Student",
                "email", "http-student@example.test", "password", "Test-password-42",
                "role", "STUDENT", "registrationNumber", "HTTP-1"), 201);
        LocalDate today = LocalDate.now(clock);
        var certificate = post("/api/certificates", csrf, Map.of("studentId", student.get("id").asLong(),
                "programCode", "JAVA", "programName", "Java Course", "certificateType", "Completion",
                "organizationName", "College", "issueDate", today.minusDays(5).toString(),
                "expiryDate", today.minusDays(1).toString()), 201);
        String id = certificate.get("certificateId").asText();
        // Wait for the real Spring scheduler, not a direct service invocation.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(certificates.findByCertificateId(id).orElseThrow().getStatus())
                        .isEqualTo(CertificateStatus.EXPIRED));
        assertThat(get("/api/public/verify/" + id).get("status").asText()).isEqualTo("EXPIRED");
        assertThat(post("/api/certificates/" + id + "/revoke", csrf,
                Map.of("reason", "HTTP test withdrawal"), 200).get("status").asText()).isEqualTo("REVOKED");
        assertThat(get("/api/public/verify/" + id).get("status").asText()).isEqualTo("REVOKED");
        post("/api/auth/logout", csrf, Map.of(), 204);
        var me = client.send(HttpRequest.newBuilder(uri("/api/auth/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(me.statusCode()).isEqualTo(401);
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }

    private JsonNode get(String path) throws Exception {
        var response = client.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(path).isEqualTo(200);
        return json.readTree(response.body());
    }

    private JsonNode post(String path, String csrf, Object data, int expected) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data))).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(path + ": " + response.body()).isEqualTo(expected);
        return expected == 204 ? null : json.readTree(response.body());
    }
}
