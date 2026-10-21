package com.certitrack.controller;

import com.certitrack.dto.response.*;
import com.certitrack.service.VerificationService;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class VerificationController {
    private final VerificationService verification;

    public VerificationController(VerificationService verification) { this.verification = verification; }

    @GetMapping({"/public/verify/{certificateId}", "/public/verify", "/public/verify/"})
    public VerificationResponse verify(@PathVariable(required = false) String certificateId) {
        return verification.verify(certificateId);
    }

    @GetMapping("/admin/verification-logs")
    public PageResponse<VerificationLogResponse> logs(Pageable pageable) { return verification.logs(pageable); }
}
