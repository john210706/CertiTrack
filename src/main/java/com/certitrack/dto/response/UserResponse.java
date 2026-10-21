package com.certitrack.dto.response;

import com.certitrack.model.entity.User;
import com.certitrack.model.enums.Role;
import java.time.LocalDateTime;

public record UserResponse(Long id, String fullName, String email, Role role,
                           String registrationNumber, boolean enabled, LocalDateTime createdAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(),
                user.getRegistrationNumber(), user.isEnabled(), user.getCreatedAt());
    }
}
