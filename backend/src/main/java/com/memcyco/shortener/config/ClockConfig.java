package com.memcyco.shortener.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class ClockConfig {

    /** System UTC clock; injected wherever code needs "now". */
    @Bean
    public Clock systemUtcClock() {
        return Clock.systemUTC();
    }
}
