package br.com.fiap.refugio.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Check(constraints = "temperatura_c between 0 and 60 and umidade_pct between 0 and 100")
public class Medicao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ponto_id", nullable = false)
    private PontoRefugio ponto;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("60.0")
    @Digits(integer = 2, fraction = 1)
    @Column(name = "temperatura_c", nullable = false, precision = 4, scale = 1)
    private BigDecimal temperaturaC;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    @Digits(integer = 3, fraction = 1)
    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal umidadePct;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrigemMedicao origem;

    @NotNull
    @Column(nullable = false)
    private Instant medidaEm;

    protected Medicao() {}

    public Medicao(
            PontoRefugio ponto,
            BigDecimal temperaturaC,
            BigDecimal umidadePct,
            OrigemMedicao origem,
            Instant medidaEm) {
        this.ponto = ponto;
        this.temperaturaC = temperaturaC;
        this.umidadePct = umidadePct;
        this.origem = origem;
        this.medidaEm = medidaEm;
    }

    public Long getId() {
        return id;
    }

    @JsonIgnore
    public PontoRefugio getPonto() {
        return ponto;
    }

    public void setPonto(PontoRefugio ponto) {
        this.ponto = ponto;
    }

    public BigDecimal getTemperaturaC() {
        return temperaturaC;
    }

    public void setTemperaturaC(BigDecimal temperaturaC) {
        this.temperaturaC = temperaturaC;
    }

    public BigDecimal getUmidadePct() {
        return umidadePct;
    }

    public void setUmidadePct(BigDecimal umidadePct) {
        this.umidadePct = umidadePct;
    }

    public OrigemMedicao getOrigem() {
        return origem;
    }

    public void setOrigem(OrigemMedicao origem) {
        this.origem = origem;
    }

    public Instant getMedidaEm() {
        return medidaEm;
    }

    public void setMedidaEm(Instant medidaEm) {
        this.medidaEm = medidaEm;
    }
}
