package com.certitrack.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record IssueCertificateRequest(
        @NotNull @Positive Long studentId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9]{2,12}") String programCode,
        @NotBlank @Size(max = 200) String programName,
        @NotBlank @Size(max = 100) String certificateType,
        @NotBlank @Size(max = 200) String organizationName,
        @NotNull LocalDate issueDate,
        LocalDate expiryDate) {}
