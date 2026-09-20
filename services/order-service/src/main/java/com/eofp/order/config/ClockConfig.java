package com.eofp.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    // Injected wherever "now" is needed, so tests can substitute a fixed clock
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
