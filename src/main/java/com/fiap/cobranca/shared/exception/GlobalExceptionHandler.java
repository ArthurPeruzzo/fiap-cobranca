package com.fiap.cobranca.shared.exception;

import com.fiap.cobranca.shared.exception.dto.ExceptionDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionDto> exceptionHandler(Exception e) {
        log.error("Erro inesperado não tratado", e);
        return resposta(500, "Ocorreu um erro inesperado: " + e.getMessage());
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ExceptionDto> baseExceptionHandler(BaseException e) {
        // 5xx é falha de infra (merece stack trace); 4xx é regra de negócio esperada (só a mensagem).
        if (e.getStatusCode() >= 500) {
            log.error(e.getMessage(), e);
        } else {
            log.warn(e.getMessage());
        }
        return resposta(e.getStatusCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ExceptionDto> illegalArgumentExceptionHandler(IllegalArgumentException e) {
        log.warn(e.getMessage());
        return resposta(400, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ExceptionDto> httpMessageNotReadableExceptionHandler(HttpMessageNotReadableException e) {
        log.warn("Corpo da requisição ilegível: {}", e.getMessage());
        return resposta(400, "Corpo da requisição inválido ou malformado");
    }

    private static ResponseEntity<ExceptionDto> resposta(int status, String mensagem) {
        return ResponseEntity.status(status)
                .body(new ExceptionDto(status, LocalDateTime.now().format(FORMATTER), mensagem));
    }
}
