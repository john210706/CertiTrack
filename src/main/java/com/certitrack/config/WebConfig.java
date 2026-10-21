package com.certitrack.config;

import com.certitrack.interceptor.EnabledUserInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final EnabledUserInterceptor enabledUsers;

    public WebConfig(EnabledUserInterceptor enabledUsers) { this.enabledUsers = enabledUsers; }

    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(enabledUsers).addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/csrf", "/api/public/**");
    }
}
