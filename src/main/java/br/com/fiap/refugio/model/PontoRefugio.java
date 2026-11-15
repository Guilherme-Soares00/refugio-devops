package br.com.fiap.refugio.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import org.hibernate.annotations.Check;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Check(
        constraints =
                "codigo_publico ~ '^REF-[0-9]{3}$' and length(trim(nome)) > 0 and length(trim(bairro)) > 0 and length(trim(horario_texto)) > 0")
public class PontoRefugio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Pattern(regexp = "REF-[0-9]{3}")
    @Column(nullable = false, unique = true, updatable = false, length = 7)
    private String codigoPublico;

    @NotBlank
    @Size(max = 110)
    @Column(nullable = false, length = 110)
    private String nome;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String logradouro;

    @NotBlank
    @Size(max = 12)
    @Column(nullable = false, length = 12)
    private String numero;

    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, length = 80)
    private String bairro;

    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, length = 80)
    private String cidade;

    @NotBlank
    @Size(min = 2, max = 2)
    @Column(nullable = false, length = 2)
    private String uf;

    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, length = 80)
    private String horarioTexto;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private StatusPonto status;

    @NotNull
    @Column(nullable = false)
    private Instant cadastradoEm;

    @JsonIgnore
    @OneToMany(mappedBy = "ponto", fetch = FetchType.LAZY)
    private List<RecursoPonto> recursos = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "ponto", fetch = FetchType.LAZY)
    private List<Medicao> medicoes = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "ponto", fetch = FetchType.LAZY)
    private List<Ocorrencia> ocorrencias = new ArrayList<>();

    protected PontoRefugio() {}

    public PontoRefugio(
            String codigoPublico,
            String nome,
            String logradouro,
            String numero,
            String bairro,
            String cidade,
            String uf,
            String horarioTexto,
            StatusPonto status,
            Instant cadastradoEm) {
        this.codigoPublico = codigoPublico;
        this.nome = nome;
        this.logradouro = logradouro;
        this.numero = numero;
        this.bairro = bairro;
        this.cidade = cidade;
        this.uf = uf;
        this.horarioTexto = horarioTexto;
        this.status = status;
        this.cadastradoEm = cadastradoEm;
    }

    public Long getId() {
        return id;
    }

    public String getCodigoPublico() {
        return codigoPublico;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getLogradouro() {
        return logradouro;
    }

    public void setLogradouro(String logradouro) {
        this.logradouro = logradouro;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public String getBairro() {
        return bairro;
    }

    public void setBairro(String bairro) {
        this.bairro = bairro;
    }

    public String getCidade() {
        return cidade;
    }

    public void setCidade(String cidade) {
        this.cidade = cidade;
    }

    public String getUf() {
        return uf;
    }

    public void setUf(String uf) {
        this.uf = uf;
    }

    public String getHorarioTexto() {
        return horarioTexto;
    }

    public void setHorarioTexto(String horarioTexto) {
        this.horarioTexto = horarioTexto;
    }

    public StatusPonto getStatus() {
        return status;
    }

    public void setStatus(StatusPonto status) {
        this.status = status;
    }

    public Instant getCadastradoEm() {
        return cadastradoEm;
    }

    public void setCadastradoEm(Instant cadastradoEm) {
        this.cadastradoEm = cadastradoEm;
    }

    @JsonIgnore
    public List<RecursoPonto> getRecursos() {
        return Collections.unmodifiableList(recursos);
    }

    @JsonIgnore
    public List<Medicao> getMedicoes() {
        return Collections.unmodifiableList(medicoes);
    }

    @JsonIgnore
    public List<Ocorrencia> getOcorrencias() {
        return Collections.unmodifiableList(ocorrencias);
    }
}
