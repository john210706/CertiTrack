package com.certitrack.service;

import com.certitrack.config.SessionIdentity;
import com.certitrack.dto.response.DashboardResponse;

public interface DashboardService {
    DashboardResponse summary(SessionIdentity identity);
}
