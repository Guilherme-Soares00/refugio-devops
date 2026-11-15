package br.com.fiap.refugio.dto;

import br.com.fiap.refugio.model.Medicao;
import br.com.fiap.refugio.model.OrigemMedicao;

import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.Instant;

@Relation(collectionRelation = "medicoes")
public record MedicaoDetail(
        Long id,
        Long pontoId,
        BigDecimal temperaturaC,
        BigDecimal umidadePct,
        OrigemMedicao origem,
        Instant medidaEm) {
    public static MedicaoDetail de(Medicao medicao) {
        return new MedicaoDetail(
                medicao.getId(),
                medicao.getPonto().getId(),
                medicao.getTemperaturaC(),
                medicao.getUmidadePct(),
                medicao.getOrigem(),
                medicao.getMedidaEm());
    }
}
