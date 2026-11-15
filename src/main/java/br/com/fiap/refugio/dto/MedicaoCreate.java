package br.com.fiap.refugio.dto;

import br.com.fiap.refugio.model.OrigemMedicao;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

public record MedicaoCreate(
        @NotNull @Positive Long pontoId,
        @NotNull @DecimalMin("0.0") @DecimalMax("60.0") @Digits(integer = 2, fraction = 1)
                BigDecimal temperaturaC,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") @Digits(integer = 3, fraction = 1)
                BigDecimal umidadePct,
        @NotNull OrigemMedicao origem,
        @NotNull Instant medidaEm) {}
