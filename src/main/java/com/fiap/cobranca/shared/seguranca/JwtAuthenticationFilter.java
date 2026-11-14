package com.fiap.cobranca.shared.seguranca;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Autenticação stateless: valida assinatura e issuer do JWT e monta as autoridades a partir da claim "roles"
 * (ex.: ROLE_ATENDENTE). Sem token a requisição segue anônima e o SecurityConfiguration responde 401.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    private static final String ROLES = "roles";

    private final Algorithm algorithm;
    private final String issuer;

    public JwtAuthenticationFilter(Algorithm algorithm, @Value("${jwt.issuer}") String issuer) {
        this.algorithm = algorithm;
        this.issuer = issuer;
    }

    /** O mock do meio de pagamento emite e valida o próprio token (não é JWT), como um meio externo faria. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/mock-pagamentos/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");

        if (authorization != null && authorization.startsWith(BEARER)) {
            try {
                DecodedJWT jwt = JWT.require(algorithm).withIssuer(issuer).build()
                        .verify(authorization.substring(BEARER.length()));
                List<GrantedAuthority> authorities = jwt.getClaim(ROLES).asList(String.class).stream()
                        .map(SimpleGrantedAuthority::new)
                        .map(GrantedAuthority.class::cast)
                        .toList();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(jwt.getSubject(), null, authorities));
            } catch (JWTVerificationException e) {
                log.warn("Falha ao verificar JWT: {}", e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Token inválido ou expirado\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
