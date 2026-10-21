package com.certitrack.service;

import com.certitrack.dto.request.*;
import com.certitrack.dto.response.*;
import org.springframework.data.domain.Pageable;
import jakarta.validation.Valid;

public interface UserService {
    UserResponse create(@Valid CreateUserRequest request);
    UserResponse authenticate(@Valid LoginRequest request);
    UserResponse get(long id);
    UserResponse requireEnabled(long id);
    PageResponse<UserResponse> list(Pageable pageable);
    UserResponse setEnabled(long actorId, long id, boolean enabled);
}
