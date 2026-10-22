package com.vego.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** Dùng Clock thay cho Instant.now() để dễ kiểm thử. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
