package com.certitrack.service;

import com.certitrack.config.SessionIdentity;
import com.certitrack.dto.request.IssueCertificateRequest;
import com.certitrack.dto.request.RevokeCertificateRequest;
import com.certitrack.dto.request.CertificateFilter;
import com.certitrack.dto.response.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;

public interface CertificateService {
    CertificateResponse issue(SessionIdentity identity, @Valid IssueCertificateRequest request);
    CertificateResponse get(SessionIdentity identity, String certificateId);
    CertificateResponse revoke(SessionIdentity identity, String certificateId, @Valid RevokeCertificateRequest request);
    PageResponse<CertificateResponse> list(SessionIdentity identity, @Valid CertificateFilter filter, Pageable pageable);
    PageResponse<StudentResponse> students(SessionIdentity identity, Pageable pageable);
    StudentResponse student(SessionIdentity identity, long id);
}
