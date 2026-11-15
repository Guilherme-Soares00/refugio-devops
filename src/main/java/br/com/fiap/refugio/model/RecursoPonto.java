package br.com.fiap.refugio.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.time.Instant;

@Entity
@Table(
        name = "recurso_ponto",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_recurso_ponto_tipo",
                        columnNames = {"ponto_id", "tipo"}))
public class RecursoPonto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ponto_id", nullable = false)
    private PontoRefugio ponto;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoRecurso tipo;

    @Column(nullable = false)
    private boolean disponivel;

    @NotNull
    @Column(nullable = false)
    private Instant atualizadoEm;

    protected RecursoPonto() {}

    public RecursoPonto(
            PontoRefugio ponto, TipoRecurso tipo, boolean disponivel, Instant atualizadoEm) {
        this.ponto = ponto;
        this.tipo = tipo;
        this.disponivel = disponivel;
        this.atualizadoEm = atualizadoEm;
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

    public TipoRecurso getTipo() {
        return tipo;
    }

    public void setTipo(TipoRecurso tipo) {
        this.tipo = tipo;
    }

    public boolean isDisponivel() {
        return disponivel;
    }

    public void setDisponivel(boolean disponivel) {
        this.disponivel = disponivel;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(Instant atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}
