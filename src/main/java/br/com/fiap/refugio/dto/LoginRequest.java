package br.com.fiap.refugio.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(@NotBlank @Size(max = 120) String email, @NotBlank String senha) {}
