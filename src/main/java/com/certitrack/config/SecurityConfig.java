package com.certitrack.config;

import com.certitrack.dto.request.LoginRequest;
import com.certitrack.dto.response.ErrorResponse;
import com.certitrack.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.authentication.session.*;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    AuthenticationManager authenticationManager(UserService users) {
        return authentication -> {
            var user = users.authenticate(new LoginRequest(authentication.getName(),
                    authentication.getCredentials().toString()));
            return UsernamePasswordAuthenticationToken.authenticated(
                    new SessionIdentity(user.id(), user.role()), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
        };
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean CsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts,
                                            CsrfTokenRepository csrf, ObjectMapper json) throws Exception {
        return http
                .securityContext(context -> context.securityContextRepository(contexts))
                .csrf(config -> config.csrfTokenRepository(csrf))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
                        + "object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/verify", "/api/public/verify/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/issuer/**").hasRole("ISSUER")
                        .requestMatchers("/student/**").hasRole("STUDENT")
                        .requestMatchers("/api/students/**", "/api/students").hasAnyRole("ADMIN", "ISSUER")
                        .requestMatchers("/api/student/**").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.POST, "/api/certificates/**").hasAnyRole("ADMIN", "ISSUER")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, ex) -> {
                            if (req.getRequestURI().startsWith("/api/"))
                                write(json, req, res, HttpStatus.UNAUTHORIZED, "Login required");
                            else res.sendRedirect("/login.html");
                        })
                        .accessDeniedHandler((req, res, ex) ->
                                write(json, req, res, HttpStatus.FORBIDDEN, "Access denied or invalid CSRF token")))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
                .build();
    }

    private static void write(ObjectMapper json, HttpServletRequest request, HttpServletResponse response,
                              HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), ErrorResponse.of(status, message, request.getRequestURI()));
    }
}
