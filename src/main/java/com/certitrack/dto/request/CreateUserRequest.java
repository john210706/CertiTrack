package com.certitrack.dto.request;

import com.certitrack.model.enums.Role;
import jakarta.validation.constraints.*;

public record CreateUserRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull Role role,
        @Size(max = 50) String registrationNumber) {
    @Override public String toString() { return "CreateUserRequest[redacted]"; }
}
