package com.certitrack.service;

import com.certitrack.dto.response.*;
import org.springframework.data.domain.Pageable;

public interface VerificationService {
    VerificationResponse verify(String certificateId);
    PageResponse<VerificationLogResponse> logs(Pageable pageable);
}
