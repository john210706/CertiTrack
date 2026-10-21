package com.certitrack.dto.response;

public record DashboardResponse(long totalCertificates, long validCertificates, long expiredCertificates,
        long revokedCertificates, Long totalStudents, Long totalIssuers, Long verificationAttemptsToday) {}
