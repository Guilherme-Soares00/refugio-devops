package br.com.fiap.refugio.config;

import br.com.fiap.refugio.dto.ApiError;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.*;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
public class SecurityErrors {
    private final ObjectMapper mapper;

    public SecurityErrors(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void escrever(HttpServletRequest request, HttpServletResponse response, int codigo)
            throws IOException {
        response.setStatus(codigo);
        response.setContentType("application/json");
        mapper.writeValue(
                response.getOutputStream(),
                new ApiError(
                        codigo,
                        HttpStatus.valueOf(codigo).getReasonPhrase(),
                        codigo == 401
                                ? "Autenticação necessária ou token inválido"
                                : "Acesso não permitido",
                        request.getRequestURI(),
                        Instant.now()));
    }
}
