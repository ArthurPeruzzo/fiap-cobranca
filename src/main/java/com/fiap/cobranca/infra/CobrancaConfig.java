package com.fiap.cobranca.infra;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class CobrancaConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
