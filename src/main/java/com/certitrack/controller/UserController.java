package com.certitrack.controller;

import com.certitrack.dto.request.*;
import com.certitrack.dto.response.*;
import com.certitrack.config.SessionIdentity;
import com.certitrack.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) { this.users = users; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) { return users.create(request); }

    @GetMapping public PageResponse<UserResponse> list(Pageable pageable) { return users.list(pageable); }

    @GetMapping("/{id}") public UserResponse get(@PathVariable long id) { return users.get(id); }

    @PatchMapping("/{id}/enabled")
    public UserResponse setEnabled(@AuthenticationPrincipal SessionIdentity identity, @PathVariable long id,
                                   @Valid @RequestBody EnabledRequest request) {
        return users.setEnabled(identity.id(), id, request.enabled());
    }
}
