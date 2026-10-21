package com.certitrack.repository;

import com.certitrack.model.entity.Certificate;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.certitrack.model.enums.CertificateStatus;
import java.time.*;

public interface CertificateRepository extends JpaRepository<Certificate, Long>, JpaSpecificationExecutor<Certificate> {
    Optional<Certificate> findByCertificateId(String certificateId);
    boolean existsByStudent_IdAndIssuedBy_Id(long studentId, long issuerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Certificate c where c.certificateId = :id")
    Optional<Certificate> findForRevocation(@Param("id") String certificateId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Certificate c set c.status = :expired, c.updatedAt = :now, c.version = c.version + 1
            where c.status = :valid and c.expiryDate < :today
            """)
    int expireEligible(@Param("valid") CertificateStatus valid, @Param("expired") CertificateStatus expired,
                       @Param("today") LocalDate today, @Param("now") LocalDateTime now);
}
