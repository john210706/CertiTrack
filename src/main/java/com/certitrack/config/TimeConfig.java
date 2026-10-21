package com.certitrack.config;

import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration
public class TimeConfig {
    @Bean
    Clock applicationClock(@Value("${certitrack.time-zone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
