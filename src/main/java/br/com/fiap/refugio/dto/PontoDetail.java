package br.com.fiap.refugio.dto;

import br.com.fiap.refugio.model.StatusPonto;

import java.time.Instant;
import java.util.List;

public record PontoDetail(
        Long id,
        String codigoPublico,
        String nome,
        String logradouro,
        String numero,
        String bairro,
        String cidade,
        String uf,
        String horarioTexto,
        StatusPonto status,
        Instant cadastradoEm,
        List<RecursoDetail> recursos,
        MedicaoDetail ultimaMedicao,
        String etiqueta,
        String textoSemMedicao,
        String avisoDados) {}
