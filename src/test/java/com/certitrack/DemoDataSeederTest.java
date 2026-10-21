package com.certitrack;

import com.certitrack.config.DemoDataSeeder;
import com.certitrack.model.enums.*;
import com.certitrack.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:certitrack-demo;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles({"test", "demo"})
class DemoDataSeederTest {
    @Autowired DemoDataSeeder seeder;
    @Autowired UserRepository users;
    @Autowired CertificateRepository certificates;
    @Autowired PasswordEncoder passwords;

    @Test
    void demoDataIsCompleteAndIdempotent() throws Exception {
        var issuer = users.findByEmail("issuer@certitrack.local").orElseThrow();
        var student = users.findByEmail("student@certitrack.local").orElseThrow();
        assertThat(users.findByEmail("admin@certitrack.local")).isPresent();
        assertThat(passwords.matches(DemoDataSeeder.PASSWORD, student.getPassword())).isTrue();
        assertThat(certificates.existsByStudent_IdAndIssuedBy_Id(student.getId(), issuer.getId())).isTrue();
        assertThat(certificates.findAll()).extracting(c -> c.getStatus())
                .containsExactlyInAnyOrder(CertificateStatus.VALID, CertificateStatus.EXPIRED, CertificateStatus.REVOKED);
        long count = certificates.count();
        seeder.run(new DefaultApplicationArguments());
        assertThat(certificates.count()).isEqualTo(count);
    }
}
