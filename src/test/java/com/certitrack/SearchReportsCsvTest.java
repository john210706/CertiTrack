package com.certitrack;

import com.certitrack.config.SessionIdentity;
import com.certitrack.dto.request.*;
import com.certitrack.model.enums.Role;
import com.certitrack.service.CertificateService;
import com.certitrack.util.Csv;
import java.io.StringWriter;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchReportsCsvTest extends ApiTestSupport {
    @Autowired CertificateService certificates;
    @Autowired Clock clock;

    @Test
    void searchAndFiltersStayScopedAndRespectEffectiveStatus() throws Exception {
        var issuer = user(Role.ISSUER);
        var student = user(Role.STUDENT);
        String marker = "Course_" + UUID.randomUUID();
        LocalDate today = LocalDate.now(clock);
        var identity = new SessionIdentity(issuer.id(), issuer.role());
        String valid = certificates.issue(identity, new IssueCertificateRequest(student.id(), "JAVA", marker,
                "Participation", "College", today.minusDays(2), today)).certificateId();
        certificates.issue(identity, new IssueCertificateRequest(student.id(), "JAVA", marker, "Completion",
                "College", today.minusDays(2), today.minusDays(1)));
        Client client = login(issuer);
        mvc.perform(get("/api/certificates").session(client.session()).param("search", marker)
                .param("status", "EXPIRED").param("certificateType", "completion")
                .param("issueYear", "" + today.getYear())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/certificates").session(client.session()).param("search", valid))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/certificates").session(client.session()).param("search", student.fullName()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        Client unrelated = login(user(Role.ISSUER));
        mvc.perform(get("/api/certificates").session(unrelated.session()).param("search", marker))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/certificates").session(client.session()).param("search", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void malformedFiltersFailAndReportsAreAdminOnly() throws Exception {
        Client issuer = login(user(Role.ISSUER));
        for (String query : java.util.List.of("issueYear=999", "issueYear=10000", "status=UNKNOWN", "issueYear=no")) {
            mvc.perform(get("/api/certificates?" + query).session(issuer.session()))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/admin/reports/summary").session(issuer.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/certificates/export").session(issuer.session())).andExpect(status().isForbidden());
        Client admin = login(user(Role.ADMIN));
        var report = body(mvc.perform(get("/api/admin/reports/summary").session(admin.session()))
                .andExpect(status().isOk()).andReturn());
        assertThat(report.get("totalCertificates").asLong()).isEqualTo(report.get("validCertificates").asLong()
                + report.get("expiredCertificates").asLong() + report.get("revokedCertificates").asLong());
        assertThat(report.get("totalStudents").asLong()).isPositive();
    }

    @Test
    void filteredCsvContainsOnlySafeFieldsAndEscapesSpreadsheetContent() throws Exception {
        var issuer = user(Role.ISSUER);
        var student = user(Role.STUDENT);
        String program = "=SUM(1,2) \"quoted\"\nNew line " + UUID.randomUUID();
        String id = certificates.issue(new SessionIdentity(issuer.id(), issuer.role()),
                new IssueCertificateRequest(student.id(), "JAVA", program, "Completion", "College",
                        LocalDate.now(clock), null)).certificateId();
        Client admin = login(user(Role.ADMIN));
        var result = mvc.perform(get("/api/admin/certificates/export").session(admin.session()).param("search", id))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", containsString(".csv")))
                .andReturn();
        String csv = result.getResponse().getContentAsString();
        assertThat(csv).contains("\"certificateId\",\"studentName\"")
                .contains("\"'=SUM(1,2) \"\"quoted\"\"\nNew line")
                .doesNotContain(student.email(), "password", "registrationNumber");
        assertThat(csv).contains(id);
    }

    @Test
    void csvWriterPreservesQuotesAndProtectsLeadingWhitespaceFormulas() throws Exception {
        var writer = new StringWriter();
        Csv.row(writer, "a,b", "a\"b", "\t=1+2", "@SUM(A1)", null);
        assertThat(writer.toString()).isEqualTo("\"a,b\",\"a\"\"b\",\"'\t=1+2\",\"'@SUM(A1)\",\"\"\r\n");
    }
}
