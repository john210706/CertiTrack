package com.certitrack.scheduler;

import com.certitrack.service.ExpirationService;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "certitrack.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class ExpirationScheduler {
    private static final Logger log = LoggerFactory.getLogger(ExpirationScheduler.class);
    private final ExpirationService expiration;

    public ExpirationScheduler(ExpirationService expiration) { this.expiration = expiration; }

    @Scheduled(cron = "${certitrack.expiry.cron}", zone = "${certitrack.time-zone}")
    public void expire() {
        int changed = expiration.expireCertificates();
        if (changed > 0) log.info("Expired {} certificates", changed);
    }
}
