package com.certitrack.dto.request;

import jakarta.validation.constraints.*;

public record RevokeCertificateRequest(@NotBlank @Size(max = 1000) String reason) {}
