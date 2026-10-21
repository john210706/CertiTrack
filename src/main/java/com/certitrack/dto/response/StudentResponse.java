package com.certitrack.dto.response;

import com.certitrack.model.entity.User;

public record StudentResponse(Long id, String fullName, String registrationNumber, boolean enabled) {
    public static StudentResponse from(User user) {
        return new StudentResponse(user.getId(), user.getFullName(), user.getRegistrationNumber(), user.isEnabled());
    }
}
