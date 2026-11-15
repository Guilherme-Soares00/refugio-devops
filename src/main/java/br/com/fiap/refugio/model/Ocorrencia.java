package br.com.fiap.refugio.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import org.hibernate.annotations.Check;

import java.time.Instant;

@Entity
@Check(constraints = "char_length(descricao) between 10 and 500")
public class Ocorrencia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ponto_id", nullable = false)
    private PontoRefugio ponto;

    @JsonIgnore
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaOcorrencia categoria;

    @NotBlank
    @Size(min = 10, max = 500)
    @Column(nullable = false, length = 500)
    private String descricao;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private PrioridadeOcorrencia prioridade;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private StatusOcorrencia status;

    @NotNull
    @Column(nullable = false)
    private Instant abertaEm;

    @Column private Instant resolvidaEm;

    protected Ocorrencia() {}

    public Ocorrencia(
            PontoRefugio ponto,
            Usuario usuario,
            CategoriaOcorrencia categoria,
            String descricao,
            PrioridadeOcorrencia prioridade,
            StatusOcorrencia status,
            Instant abertaEm,
            Instant resolvidaEm) {
        this.ponto = ponto;
        this.usuario = usuario;
        this.categoria = categoria;
        this.descricao = descricao;
        this.prioridade = prioridade;
        this.status = status;
        this.abertaEm = abertaEm;
        this.resolvidaEm = resolvidaEm;
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

    @JsonIgnore
    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public CategoriaOcorrencia getCategoria() {
        return categoria;
    }

    public void setCategoria(CategoriaOcorrencia categoria) {
        this.categoria = categoria;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public PrioridadeOcorrencia getPrioridade() {
        return prioridade;
    }

    public void setPrioridade(PrioridadeOcorrencia prioridade) {
        this.prioridade = prioridade;
    }

    public StatusOcorrencia getStatus() {
        return status;
    }

    public void setStatus(StatusOcorrencia status) {
        this.status = status;
    }

    public Instant getAbertaEm() {
        return abertaEm;
    }

    public void setAbertaEm(Instant abertaEm) {
        this.abertaEm = abertaEm;
    }

    public Instant getResolvidaEm() {
        return resolvidaEm;
    }

    public void setResolvidaEm(Instant resolvidaEm) {
        this.resolvidaEm = resolvidaEm;
    }
}
