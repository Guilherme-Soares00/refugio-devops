package br.com.fiap.refugio.config;

import br.com.fiap.refugio.model.CategoriaOcorrencia;
import br.com.fiap.refugio.model.Medicao;
import br.com.fiap.refugio.model.Ocorrencia;
import br.com.fiap.refugio.model.OrigemMedicao;
import br.com.fiap.refugio.model.Papel;
import br.com.fiap.refugio.model.PontoRefugio;
import br.com.fiap.refugio.model.PrioridadeOcorrencia;
import br.com.fiap.refugio.model.RecursoPonto;
import br.com.fiap.refugio.model.StatusOcorrencia;
import br.com.fiap.refugio.model.StatusPonto;
import br.com.fiap.refugio.model.TipoRecurso;
import br.com.fiap.refugio.model.Usuario;
import br.com.fiap.refugio.repository.MedicaoRepository;
import br.com.fiap.refugio.repository.OcorrenciaRepository;
import br.com.fiap.refugio.repository.PontoRefugioRepository;
import br.com.fiap.refugio.repository.RecursoPontoRepository;
import br.com.fiap.refugio.repository.UsuarioRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class SeedDemonstrativo implements CommandLineRunner {
    private static final Instant CADASTRO = Instant.parse("2026-11-09T09:00:00Z");
    private final UsuarioRepository usuarios;
    private final PontoRefugioRepository pontos;
    private final RecursoPontoRepository recursos;
    private final MedicaoRepository medicoes;
    private final OcorrenciaRepository ocorrencias;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public SeedDemonstrativo(
            UsuarioRepository usuarios,
            PontoRefugioRepository pontos,
            RecursoPontoRepository recursos,
            MedicaoRepository medicoes,
            OcorrenciaRepository ocorrencias) {
        this.usuarios = usuarios;
        this.pontos = pontos;
        this.recursos = recursos;
        this.medicoes = medicoes;
        this.ocorrencias = ocorrencias;
    }

    @Override
    @Transactional
    public void run(String... args) throws IOException {
        for (String[] linha : linhas("usuarios.csv")) {
            if (usuarios.findByEmail(linha[1]).isEmpty()) {
                Papel papel = Papel.valueOf(linha[2]);
                String senha = papel == Papel.GESTOR ? "GestorDemo2026!" : "CidadaoDemo2026!";
                usuarios.save(
                        new Usuario(
                                linha[0], linha[1], encoder.encode(senha), papel, true, CADASTRO));
            }
        }
        for (String[] linha : linhas("pontos.csv")) {
            if (pontos.findByCodigoPublico(linha[0]).isEmpty()) {
                int numeroExemplo = Integer.parseInt(linha[0].substring(4));
                pontos.save(
                        new PontoRefugio(
                                linha[0],
                                linha[1],
                                "Rua Exemplo " + numeroExemplo,
                                "100",
                                linha[2],
                                "Cidade Demonstrativa",
                                "SP",
                                linha[3],
                                StatusPonto.valueOf(linha[4]),
                                CADASTRO));
            }
        }
        for (String[] linha : linhas("recursos.csv")) {
            PontoRefugio ponto = ponto(linha[0]);
            TipoRecurso tipo = TipoRecurso.valueOf(linha[1]);
            if (!recursos.existsByPontoAndTipo(ponto, tipo)) {
                recursos.save(
                        new RecursoPonto(ponto, tipo, Boolean.parseBoolean(linha[2]), CADASTRO));
            }
        }
        for (String[] linha : linhas("medicoes.csv")) {
            PontoRefugio ponto = ponto(linha[0]);
            Instant medidaEm = Instant.parse(linha[3]);
            if (!medicoes.existsByPontoAndOrigemAndMedidaEm(
                    ponto, OrigemMedicao.SIMULADA, medidaEm)) {
                medicoes.save(
                        new Medicao(
                                ponto,
                                new BigDecimal(linha[1]),
                                new BigDecimal(linha[2]),
                                OrigemMedicao.SIMULADA,
                                medidaEm));
            }
        }
        for (String[] linha : linhas("ocorrencias.csv")) {
            PontoRefugio ponto = ponto(linha[0]);
            Usuario usuario = usuarios.findByEmail(linha[1]).orElseThrow();
            CategoriaOcorrencia categoria = CategoriaOcorrencia.valueOf(linha[2]);
            Instant abertaEm = Instant.parse(linha[6]);
            if (!ocorrencias.existsByPontoAndUsuarioAndCategoriaAndAbertaEm(
                    ponto, usuario, categoria, abertaEm)) {
                ocorrencias.save(
                        new Ocorrencia(
                                ponto,
                                usuario,
                                categoria,
                                linha[5],
                                PrioridadeOcorrencia.valueOf(linha[3]),
                                StatusOcorrencia.valueOf(linha[4]),
                                abertaEm,
                                linha[7].isEmpty() ? null : Instant.parse(linha[7])));
            }
        }
    }

    private PontoRefugio ponto(String codigo) {
        return pontos.findByCodigoPublico(codigo).orElseThrow();
    }

    private List<String[]> linhas(String arquivo) throws IOException {
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                new ClassPathResource("seed/" + arquivo).getInputStream(),
                                StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(linha -> !linha.isBlank())
                    .map(linha -> linha.split("\\|", -1))
                    .toList();
        }
    }
}
