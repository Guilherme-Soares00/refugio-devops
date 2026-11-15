package br.com.fiap.refugio.dto;

import br.com.fiap.refugio.model.StatusOcorrencia;

import jakarta.validation.constraints.NotNull;

public record OcorrenciaStatusUpdate(@NotNull StatusOcorrencia status) {}
