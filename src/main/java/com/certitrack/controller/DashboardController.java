package com.certitrack.controller;

import com.certitrack.config.SessionIdentity;
import com.certitrack.dto.response.DashboardResponse;
import com.certitrack.service.DashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class DashboardController {
    private final DashboardService dashboard;
    public DashboardController(DashboardService dashboard) { this.dashboard = dashboard; }

    @GetMapping("/api/dashboard")
    public DashboardResponse summary(@AuthenticationPrincipal SessionIdentity identity) {
        return dashboard.summary(identity);
    }
}
