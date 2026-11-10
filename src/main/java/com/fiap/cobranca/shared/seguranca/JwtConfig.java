package com.fiap.cobranca.shared.seguranca;

import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;

/** Mesmo segredo e issuer da API principal (fiap-mecanica): o token emitido lá é validado aqui, sem banco de usuários. */
@Configuration
public class JwtConfig {

    @Bean
    public Algorithm jwtAlgorithm(@Value("${jwt.secret}") String secretKey) {
        return Algorithm.HMAC256(Base64.getUrlDecoder().decode(secretKey));
    }
}
