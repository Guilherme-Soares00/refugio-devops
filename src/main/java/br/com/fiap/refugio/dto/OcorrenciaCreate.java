package br.com.fiap.refugio.dto;

import br.com.fiap.refugio.model.CategoriaOcorrencia;

import jakarta.validation.constraints.*;

public record OcorrenciaCreate(
        @NotNull @Positive Long pontoId,
        @NotNull CategoriaOcorrencia categoria,
        @NotBlank @Size(min = 10, max = 500) String descricao) {}
