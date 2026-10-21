package com.certitrack.controller;

import com.certitrack.config.SessionIdentity;
import com.certitrack.dto.request.CertificateFilter;
import com.certitrack.dto.response.DashboardResponse;
import com.certitrack.service.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class ReportController {
    private final DashboardService dashboard;
    private final CsvExportService csv;

    public ReportController(DashboardService dashboard, CsvExportService csv) {
        this.dashboard = dashboard;
        this.csv = csv;
    }

    @GetMapping("/reports/summary")
    public DashboardResponse summary(@AuthenticationPrincipal SessionIdentity identity) {
        return dashboard.summary(identity);
    }

    @GetMapping(value = "/certificates/export", produces = "text/csv")
    public void export(@AuthenticationPrincipal SessionIdentity identity,
                       @Valid @ModelAttribute CertificateFilter filter,
                       HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=\"certitrack-certificates.csv\"");
        csv.export(identity, filter, response.getWriter());
    }
}
