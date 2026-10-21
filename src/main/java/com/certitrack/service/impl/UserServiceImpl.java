package com.certitrack.service.impl;

import com.certitrack.dto.request.*;
import com.certitrack.dto.response.*;
import com.certitrack.exception.*;
import com.certitrack.model.entity.User;
import com.certitrack.model.enums.Role;
import com.certitrack.repository.UserRepository;
import com.certitrack.service.UserService;
import com.certitrack.util.PageRequests;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import static org.springframework.http.HttpStatus.*;

@Service
@Validated
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String dummyHash;
    private final Clock clock;

    public UserServiceImpl(UserRepository users, PasswordEncoder passwords, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.clock = clock;
        this.dummyHash = passwords.encode("unmatchable-" + java.util.UUID.randomUUID());
    }

    @Override @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) throw new DuplicateEmailException();
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new DomainException(BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes");
        String registration = request.registrationNumber() == null ? null : request.registrationNumber().trim();
        if (request.role() == Role.STUDENT && (registration == null || registration.isBlank()))
            throw new DomainException(BAD_REQUEST, "Student registration number is required");
        User user = new User(request.fullName().trim(), email, passwords.encode(request.password()),
                request.role(), registration, LocalDateTime.now(clock));
        return UserResponse.from(users.saveAndFlush(user));
    }

    @Override
    public UserResponse authenticate(LoginRequest request) {
        User user = users.findByEmail(request.email().trim().toLowerCase(Locale.ROOT)).orElse(null);
        boolean matches = request.password().getBytes(StandardCharsets.UTF_8).length <= 72
                && passwords.matches(request.password(), user == null ? dummyHash : user.getPassword());
        if (!matches || user == null) throw new InvalidCredentialsException();
        if (!user.isEnabled()) throw new DisabledUserException();
        return UserResponse.from(user);
    }

    @Override public UserResponse get(long id) { return UserResponse.from(find(id)); }

    @Override
    public UserResponse requireEnabled(long id) {
        User user = find(id);
        if (!user.isEnabled()) throw new DisabledUserException();
        return UserResponse.from(user);
    }

    @Override
    public PageResponse<UserResponse> list(Pageable pageable) {
        return PageResponse.from(users.findAll(PageRequests.safe(pageable)).map(UserResponse::from));
    }

    @Override @Transactional
    public UserResponse setEnabled(long actorId, long id, boolean enabled) {
        if (actorId == id && !enabled)
            throw new DomainException(BAD_REQUEST, "You cannot disable your own account");
        User user = find(id);
        user.setEnabled(enabled);
        return UserResponse.from(user);
    }

    private User find(long id) {
        return users.findById(id).orElseThrow(UserNotFoundException::new);
    }
}
