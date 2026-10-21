package com.certitrack;

import com.certitrack.config.SessionIdentity;
import com.certitrack.dto.request.CreateUserRequest;
import com.certitrack.model.enums.Role;
import com.certitrack.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAuthenticationTest extends ApiTestSupport {
    @Autowired UserRepository repository;
    @Autowired PasswordEncoder passwords;

    @Test
    void loginRotatesSessionAndStoresOnlySafeIdentityThenLogoutInvalidatesIt() throws Exception {
        var student = user(Role.STUDENT);
        Client client = anonymous();
        String oldId = client.session().getId();
        mvc.perform(post("/api/auth/login").session(client.session()).header("X-CSRF-TOKEN", client.csrf())
                .contentType(APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("email", student.email(), "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist());
        assertThat(client.session().getId()).isNotEqualTo(oldId);
        var context = (SecurityContext) client.session().getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(context.getAuthentication().getPrincipal()).isEqualTo(new SessionIdentity(student.id(), Role.STUDENT));
        assertThat(context.getAuthentication().getCredentials()).isNull();
        mvc.perform(get("/api/auth/me").session(client.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(student.id()));
        var token = body(mvc.perform(get("/api/auth/csrf").session(client.session())).andReturn()).get("token").asText();
        mvc.perform(post("/api/auth/logout").session(client.session()).header("X-CSRF-TOKEN", token))
                .andExpect(status().isNoContent());
        assertThat(client.session().isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void administratorsCanCreateUsersAndPasswordsAreBcrypt() throws Exception {
        Client admin = login(user(Role.ADMIN));
        String email = java.util.UUID.randomUUID() + "@example.test";
        var result = mvc.perform(post("/api/admin/users").session(admin.session()).header("X-CSRF-TOKEN", admin.csrf())
                .contentType(APPLICATION_JSON).content(json.writeValueAsString(
                        new CreateUserRequest("Jane", email, PASSWORD, Role.STUDENT, "STU-1"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.password").doesNotExist()).andReturn();
        var saved = repository.findById(body(result).get("id").asLong()).orElseThrow();
        assertThat(saved.getPassword()).startsWith("$2");
        assertThat(passwords.matches(PASSWORD, saved.getPassword())).isTrue();
        mvc.perform(get("/api/admin/users").session(admin.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void issuerAndStudentCannotManageUsers() throws Exception {
        for (Role role : new Role[] { Role.ISSUER, Role.STUDENT }) {
            Client client = login(user(role));
            mvc.perform(get("/api/admin/users").session(client.session())).andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/users").session(client.session()).header("X-CSRF-TOKEN", client.csrf())
                    .contentType(APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        }
    }

    @Test
    void disabledAccountCannotLogInOrContinueAnExistingSession() throws Exception {
        var student = user(Role.STUDENT);
        var admin = user(Role.ADMIN);
        Client client = login(student);
        users.setEnabled(admin.id(), student.id(), false);
        mvc.perform(get("/api/auth/me").session(client.session())).andExpect(status().isForbidden());
        assertThat(client.session().isInvalid()).isTrue();
        Client anonymous = anonymous();
        mvc.perform(post("/api/auth/login").session(anonymous.session()).header("X-CSRF-TOKEN", anonymous.csrf())
                .contentType(APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("email", student.email(), "password", PASSWORD))))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCannotDisableOwnAccount() throws Exception {
        var adminUser = user(Role.ADMIN);
        Client admin = login(adminUser);
        mvc.perform(patch("/api/admin/users/" + adminUser.id() + "/enabled")
                .session(admin.session()).header("X-CSRF-TOKEN", admin.csrf())
                .contentType(APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot disable your own account"));
        assertThat(repository.findById(adminUser.id()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void badCredentialsAndMissingCsrfAreRejected() throws Exception {
        var student = user(Role.STUDENT);
        Client client = anonymous();
        String data = json.writeValueAsString(Map.of("email", student.email(), "password", "wrong"));
        mvc.perform(post("/api/auth/login").session(client.session()).contentType(APPLICATION_JSON).content(data))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(post("/api/auth/login").session(client.session()).header("X-CSRF-TOKEN", client.csrf())
                .contentType(APPLICATION_JSON).content(data)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void validationAndDuplicateEmailErrorsAreSafe() throws Exception {
        Client admin = login(user(Role.ADMIN));
        var student = user(Role.STUDENT);
        mvc.perform(post("/api/admin/users").session(admin.session()).header("X-CSRF-TOKEN", admin.csrf())
                .contentType(APPLICATION_JSON).content(json.writeValueAsString(new CreateUserRequest(
                        "Duplicate", student.email().toUpperCase(), PASSWORD, Role.STUDENT, "REG"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.trace").doesNotExist());
        mvc.perform(post("/api/admin/users").session(admin.session()).header("X-CSRF-TOKEN", admin.csrf())
                .contentType(APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        assertThatThrownBy(() -> users.create(new CreateUserRequest("No registration",
                "missing-reg@example.test", PASSWORD, Role.STUDENT, " "))).hasMessageContaining("registration");
        assertThatThrownBy(() -> users.create(new CreateUserRequest("Long bytes",
                "long-bytes@example.test", "é".repeat(40), Role.ISSUER, null))).hasMessageContaining("72");
    }
}
