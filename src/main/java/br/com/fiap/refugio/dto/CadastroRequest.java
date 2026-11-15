package br.com.fiap.refugio.dto;

import jakarta.validation.constraints.*;

import java.util.Locale;

public record CadastroRequest(
        @NotBlank @Size(max = 90) String nome,
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank String senha) {
    public CadastroRequest {
        if (email != null) email = email.trim().toLowerCase(Locale.ROOT);
    }
}
