package br.com.fiap.refugio.dto;

import br.com.fiap.refugio.model.TipoRecurso;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RecursoCreate(
        @NotNull @Positive Long pontoId, @NotNull TipoRecurso tipo, @NotNull Boolean disponivel) {}
