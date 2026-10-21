package com.certitrack;

import com.certitrack.model.enums.CertificateStatus;
import com.certitrack.repository.CertificateRepository;
import com.certitrack.scheduler.ExpirationScheduler;
import com.certitrack.service.ExpirationService;
import com.certitrack.service.impl.ExpirationServiceImpl;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ExpirationServiceTest {
    @Test
    void reusableServiceUsesTheConfiguredClockDateAndOnlyValidStatus() {
        var repository = mock(CertificateRepository.class);
        var clock = Clock.fixed(Instant.parse("2026-09-24T20:00:00Z"), ZoneId.of("Asia/Kolkata"));
        var now = LocalDateTime.of(2026, 9, 25, 1, 30);
        when(repository.expireEligible(CertificateStatus.VALID, CertificateStatus.EXPIRED, now.toLocalDate(), now))
                .thenReturn(3);
        assertThat(new ExpirationServiceImpl(repository, clock).expireCertificates()).isEqualTo(3);
        verify(repository).expireEligible(CertificateStatus.VALID, CertificateStatus.EXPIRED, now.toLocalDate(), now);
    }

    @Test
    void schedulerDelegatesToTheTransactionalService() {
        var expiration = mock(ExpirationService.class);
        new ExpirationScheduler(expiration).expire();
        verify(expiration).expireCertificates();
    }
}
