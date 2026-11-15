package br.com.fiap.refugio.dto;

import br.com.fiap.refugio.model.StatusPonto;

import jakarta.validation.constraints.*;

public record PontoCreate(
        @NotBlank @Pattern(regexp = "REF-[0-9]{3}") String codigoPublico,
        @NotBlank @Size(max = 110) String nome,
        @NotBlank @Size(max = 120) String logradouro,
        @NotBlank @Size(max = 12) String numero,
        @NotBlank @Size(max = 80) String bairro,
        @NotBlank @Size(max = 80) String cidade,
        @NotBlank @Size(min = 2, max = 2) String uf,
        @NotBlank @Size(max = 80) String horarioTexto,
        @NotNull StatusPonto status) {}
