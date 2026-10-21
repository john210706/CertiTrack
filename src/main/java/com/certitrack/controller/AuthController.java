package com.certitrack.controller;

import com.certitrack.config.SessionIdentity;
import com.certitrack.dto.request.LoginRequest;
import com.certitrack.dto.response.UserResponse;
import com.certitrack.service.UserService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.security.authentication.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authentication;
    private final SessionAuthenticationStrategy sessions;
    private final SecurityContextRepository contexts;
    private final UserService users;

    public AuthController(AuthenticationManager authentication, SessionAuthenticationStrategy sessions,
                          SecurityContextRepository contexts, UserService users) {
        this.authentication = authentication;
        this.sessions = sessions;
        this.contexts = contexts;
        this.users = users;
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) { return token; }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                              HttpServletResponse response) {
        var authenticated = authentication.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.email(), body.password()));
        sessions.onAuthentication(authenticated, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticated);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return users.get(((SessionIdentity) authenticated.getPrincipal()).id());
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal SessionIdentity identity) {
        return users.requireEnabled(identity.id());
    }
}
