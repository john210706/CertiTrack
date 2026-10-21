package com.certitrack.dto.request;

import com.certitrack.model.enums.CertificateStatus;
import jakarta.validation.constraints.*;

public record CertificateFilter(@Size(max = 200) String search, CertificateStatus status,
                                @Size(max = 100) String certificateType,
                                @Min(1000) @Max(9999) Integer issueYear) {}
