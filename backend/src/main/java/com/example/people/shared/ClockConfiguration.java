package com.example.people.shared;

import java.time.Clock;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfiguration {
    @Bean
    public Clock applicationClock() { return Clock.systemUTC(); }

    @Bean
    public ValidationConfigurationCustomizer validationClock(Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock);
    }
}
