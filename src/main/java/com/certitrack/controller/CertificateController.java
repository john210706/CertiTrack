package com.certitrack.controller;

import com.certitrack.config.SessionIdentity;
import com.certitrack.dto.request.IssueCertificateRequest;
import com.certitrack.dto.request.RevokeCertificateRequest;
import com.certitrack.dto.request.CertificateFilter;
import com.certitrack.dto.response.*;
import com.certitrack.service.CertificateService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CertificateController {
    private final CertificateService certificates;

    public CertificateController(CertificateService certificates) { this.certificates = certificates; }

    @PostMapping("/certificates") @ResponseStatus(HttpStatus.CREATED)
    public CertificateResponse issue(@AuthenticationPrincipal SessionIdentity identity,
                                     @Valid @RequestBody IssueCertificateRequest request) {
        return certificates.issue(identity, request);
    }

    @GetMapping({"/certificates", "/student/certificates"})
    public PageResponse<CertificateResponse> list(@AuthenticationPrincipal SessionIdentity identity,
            @Valid @ModelAttribute CertificateFilter filter, Pageable pageable) {
        return certificates.list(identity, filter, pageable);
    }

    @PostMapping("/certificates/{certificateId}/revoke")
    public CertificateResponse revoke(@AuthenticationPrincipal SessionIdentity identity,
                                      @PathVariable String certificateId,
                                      @Valid @RequestBody RevokeCertificateRequest request) {
        return certificates.revoke(identity, certificateId, request);
    }

    @GetMapping("/certificates/{certificateId}")
    public CertificateResponse get(@AuthenticationPrincipal SessionIdentity identity, @PathVariable String certificateId) {
        return certificates.get(identity, certificateId);
    }

    @GetMapping("/students")
    public PageResponse<StudentResponse> students(@AuthenticationPrincipal SessionIdentity identity, Pageable pageable) {
        return certificates.students(identity, pageable);
    }

    @GetMapping("/students/{id}")
    public StudentResponse student(@AuthenticationPrincipal SessionIdentity identity, @PathVariable long id) {
        return certificates.student(identity, id);
    }
}
