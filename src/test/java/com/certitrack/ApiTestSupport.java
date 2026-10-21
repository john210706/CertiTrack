package com.certitrack;

import com.certitrack.dto.request.CreateUserRequest;
import com.certitrack.dto.response.UserResponse;
import com.certitrack.model.enums.Role;
import com.certitrack.service.UserService;
import com.fasterxml.jackson.databind.*;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

abstract class ApiTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserService users;
    static final String PASSWORD = "Test-password-42";

    UserResponse user(Role role) {
        return users.create(new CreateUserRequest(role.name() + " Test",
                UUID.randomUUID() + "@example.test", PASSWORD, role, "REG-123"));
    }

    JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    record Client(MockHttpSession session, String csrf) {}

    Client anonymous() throws Exception {
        var result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        return new Client((MockHttpSession) result.getRequest().getSession(), body(result).get("token").asText());
    }

    Client login(UserResponse user) throws Exception {
        Client before = anonymous();
        mvc.perform(post("/api/auth/login").session(before.session()).header("X-CSRF-TOKEN", before.csrf())
                .contentType(APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("email", user.email(), "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist());
        var token = mvc.perform(get("/api/auth/csrf").session(before.session())).andReturn();
        return new Client(before.session(), body(token).get("token").asText());
    }
}
