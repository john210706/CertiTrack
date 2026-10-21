package com.certitrack.repository;

import com.certitrack.model.entity.VerificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationLogRepository extends JpaRepository<VerificationLog, Long> {
    long countByVerifiedAtGreaterThanEqualAndVerifiedAtLessThan(
            java.time.LocalDateTime start, java.time.LocalDateTime end);
}
