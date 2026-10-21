package com.certitrack.interceptor;

import com.certitrack.config.SessionIdentity;
import com.certitrack.exception.DomainException;
import com.certitrack.service.UserService;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class EnabledUserInterceptor implements HandlerInterceptor {
    private final UserService users;

    public EnabledUserInterceptor(UserService users) { this.users = users; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SessionIdentity identity) {
            try {
                users.requireEnabled(identity.id());
            } catch (DomainException ex) {
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                throw ex;
            }
        }
        return true;
    }
}
