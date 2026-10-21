package com.certitrack.config;

import com.certitrack.dto.request.CreateUserRequest;
import com.certitrack.model.enums.Role;
import com.certitrack.repository.UserRepository;
import com.certitrack.service.UserService;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("bootstrap-admin")
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository repository;
    private final UserService users;
    private final Environment environment;

    public AdminBootstrap(UserRepository repository, UserService users, Environment environment) {
        this.repository = repository;
        this.users = users;
        this.environment = environment;
    }

    @Override public void run(ApplicationArguments args) {
        if (repository.existsByRole(Role.ADMIN)) return;
        users.create(new CreateUserRequest(environment.getRequiredProperty("BOOTSTRAP_ADMIN_NAME"),
                environment.getRequiredProperty("BOOTSTRAP_ADMIN_EMAIL"),
                environment.getRequiredProperty("BOOTSTRAP_ADMIN_PASSWORD"), Role.ADMIN, null));
    }
}
