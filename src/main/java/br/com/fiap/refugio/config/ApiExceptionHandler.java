package br.com.fiap.refugio.config;

import br.com.fiap.refugio.dto.ApiError;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> negocio(ApiException ex, HttpServletRequest request) {
        return resposta(ex.getStatus(), ex.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integridade(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        return resposta(
                HttpStatus.CONFLICT,
                "Operação conflita com os dados existentes ou vínculos do registro",
                request);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> entrada(Exception ex, HttpServletRequest request) {
        return resposta(HttpStatus.BAD_REQUEST, "Dados da requisição inválidos", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> inesperado(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse erro) {
            HttpStatus status = HttpStatus.valueOf(erro.getStatusCode().value());
            return ResponseEntity.status(status)
                    .headers(erro.getHeaders())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                            new ApiError(
                                    status.value(),
                                    status.getReasonPhrase(),
                                    status.getReasonPhrase(),
                                    request.getRequestURI(),
                                    Instant.now()));
        }
        LOG.error("Erro inesperado ao processar {}", request.getRequestURI(), ex);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor", request);
    }

    private ResponseEntity<ApiError> resposta(
            HttpStatus status, String mensagem, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        new ApiError(
                                status.value(),
                                status.getReasonPhrase(),
                                mensagem,
                                request.getRequestURI(),
                                Instant.now()));
    }
}
