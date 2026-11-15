package br.com.fiap.refugio;

import br.com.fiap.refugio.config.SeedDemonstrativo;
import br.com.fiap.refugio.repository.MedicaoRepository;
import br.com.fiap.refugio.repository.OcorrenciaRepository;
import br.com.fiap.refugio.repository.PontoRefugioRepository;
import br.com.fiap.refugio.repository.RecursoPontoRepository;
import br.com.fiap.refugio.repository.UsuarioRepository;


import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DominioPostgresTest extends SeedPostgresTest {
    private final SeedDemonstrativo seed;
    private final UsuarioRepository usuarios;
    private final PontoRefugioRepository pontos;
    private final RecursoPontoRepository recursos;
    private final MedicaoRepository medicoes;
    private final OcorrenciaRepository ocorrencias;
    private final EntityManager entityManager;
    private final ObjectMapper mapper;

    @Autowired
    DominioPostgresTest(JdbcTemplate jdbc, SeedDemonstrativo seed, UsuarioRepository usuarios,
                        PontoRefugioRepository pontos, RecursoPontoRepository recursos, MedicaoRepository medicoes,
                        OcorrenciaRepository ocorrencias, EntityManager entityManager, ObjectMapper mapper) {
        super(jdbc);
        this.seed = seed;
        this.usuarios = usuarios;
        this.pontos = pontos;
        this.recursos = recursos;
        this.medicoes = medicoes;
        this.ocorrencias = ocorrencias;
        this.entityManager = entityManager;
        this.mapper = mapper;
    }

    @Test
    void preservaTodosOsValoresAprovadosNoGuia() throws IOException {
        conferir("usuarios", "select concat_ws('|', nome, email, papel) from usuario");
        conferir("pontos", "select concat_ws('|', codigo_publico, nome, bairro, horario_texto, status) from ponto_refugio");
        conferir("recursos", "select concat_ws('|', p.codigo_publico, r.tipo, cast(r.disponivel as text)) from recurso_ponto r join ponto_refugio p on p.id=r.ponto_id");
        conferir("medicoes", "select concat_ws('|', p.codigo_publico, m.temperatura_c, m.umidade_pct, to_char(m.medida_em at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"')) from medicao m join ponto_refugio p on p.id=m.ponto_id");
        conferir("ocorrencias", "select concat_ws('|', p.codigo_publico, u.email, o.categoria, o.prioridade, o.status, o.descricao, to_char(o.aberta_em at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"'), coalesce(to_char(o.resolvida_em at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"'), '')) from ocorrencia o join ponto_refugio p on p.id=o.ponto_id join usuario u on u.id=o.usuario_id");
        assertThat(jdbc.queryForObject("select count(*) from ponto_refugio where logradouro = 'Rua Exemplo ' || cast(substring(codigo_publico, 5) as integer) and numero='100' and cidade='Cidade Demonstrativa' and uf='SP' and cadastrado_em='2026-11-09T09:00:00Z'", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from usuario where ativo and criado_em='2026-11-09T09:00:00Z'", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from recurso_ponto where atualizado_em='2026-11-09T09:00:00Z'", Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("select count(*) from medicao where origem='SIMULADA'", Integer.class)).isEqualTo(10);
    }

    @Test
    void geraBCryptPorUsuarioComSenhaDemonstrativaPorPapel() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        usuarios.findAll().forEach(usuario -> {
            String senha = usuario.getPapel().name().equals("GESTOR") ? "GestorDemo2026!" : "CidadaoDemo2026!";
            assertThat(encoder.matches(senha, usuario.getSenhaHash())).isTrue();
            assertThat(encoder.matches("incorreta", usuario.getSenhaHash())).isFalse();
        });
        assertThat(jdbc.queryForObject("select count(distinct senha_hash) from usuario", Integer.class)).isEqualTo(10);
    }

    @Test
    @Transactional
    void repetirSeedNaoDuplicaNemSobrescreveAlteracoesOuHashes() throws IOException {
        jdbc.update("update ponto_refugio set nome='Nome ajustado', status='INATIVO' where codigo_publico='REF-001'");
        jdbc.update("update usuario set nome='Pessoa ajustada', ativo=false where email='cidadao01@example.test'");
        jdbc.update("update recurso_ponto set disponivel=false where ponto_id=(select id from ponto_refugio where codigo_publico='REF-001')");
        jdbc.update("update ocorrencia set status='RESOLVIDA', resolvida_em='2026-11-10T15:00:00Z' where aberta_em='2026-11-09T11:01:00Z'");
        var antes = snapshot();
        seed.run();
        seed.run();
        entityManager.flush();
        assertThat(snapshot()).isEqualTo(antes);
    }

    @Test
    @Transactional
    void completaCargaParcialSemRecriarRegistrosExistentes() throws IOException {
        jdbc.update("delete from medicao where ponto_id=(select id from ponto_refugio where codigo_publico='REF-003')");
        jdbc.update("delete from recurso_ponto where tipo='AGUA' and ponto_id=(select id from ponto_refugio where codigo_publico='REF-003')");
        jdbc.update("delete from ocorrencia where aberta_em='2026-11-09T11:03:00Z'");
        seed.run();
        entityManager.flush();
        carregaAsCincoTabelasComQuantidadesAprovadasNoPostgres();
        preservaTodosOsValoresAprovadosNoGuia();
    }

    @Test
    @Transactional
    void relacionamentosLazyNaoSaoSerializadosNemExcluidosEmCascata() throws Exception {
        entityManager.clear();
        var ponto = pontos.findByCodigoPublico("REF-001").orElseThrow();
        assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(ponto, "recursos")).isFalse();
        assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(ponto, "medicoes")).isFalse();
        assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(ponto, "ocorrencias")).isFalse();
        var pontoJson = mapper.readTree(mapper.writeValueAsString(ponto));
        assertThat(pontoJson.has("recursos") || pontoJson.has("medicoes") || pontoJson.has("ocorrencias")).isFalse();
        var usuario = usuarios.findByEmail("cidadao01@example.test").orElseThrow();
        assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(usuario, "ocorrencias")).isFalse();
        var usuarioJson = mapper.readTree(mapper.writeValueAsString(usuario));
        assertThat(usuarioJson.has("senhaHash") || usuarioJson.has("ocorrencias")).isFalse();
        entityManager.clear();
        var recurso = recursos.findAll().getFirst();
        assertThat(Hibernate.isInitialized(recurso.getPonto())).isFalse();
        assertThat(mapper.readTree(mapper.writeValueAsString(recurso)).has("ponto")).isFalse();
        entityManager.clear();
        var medicao = medicoes.findAll().getFirst();
        assertThat(Hibernate.isInitialized(medicao.getPonto())).isFalse();
        assertThat(mapper.readTree(mapper.writeValueAsString(medicao)).has("ponto")).isFalse();
        entityManager.clear();
        var ocorrencia = ocorrencias.findAll().getFirst();
        assertThat(Hibernate.isInitialized(ocorrencia.getPonto())).isFalse();
        assertThat(Hibernate.isInitialized(ocorrencia.getUsuario())).isFalse();
        var ocorrenciaJson = mapper.readTree(mapper.writeValueAsString(ocorrencia));
        assertThat(ocorrenciaJson.has("ponto") || ocorrenciaJson.has("usuario")).isFalse();
    }

    @Test
    @Transactional
    void atualizaCamposMutaveisMantendoCodigoPublico() {
        var ponto = pontos.findByCodigoPublico("REF-001").orElseThrow();
        ponto.setNome("Nome novo");
        ponto.setHorarioTexto("10:00-14:00");
        entityManager.flush();
        entityManager.clear();
        var recarregado = pontos.findById(ponto.getId()).orElseThrow();
        assertThat(recarregado.getNome()).isEqualTo("Nome novo");
        assertThat(recarregado.getHorarioTexto()).isEqualTo("10:00-14:00");
        assertThat(recarregado.getCodigoPublico()).isEqualTo("REF-001");
    }

    @Test
    @Transactional
    void codigoPublicoMarcadoNaoAtualizavelPermaneceNoBancoMesmoSeCampoForAlterado() throws Exception {
        var ponto = pontos.findByCodigoPublico("REF-001").orElseThrow();
        var campo = ponto.getClass().getDeclaredField("codigoPublico");
        campo.setAccessible(true);
        campo.set(ponto, "REF-111");
        ponto.setNome("Nome atualizado");
        entityManager.flush();
        entityManager.clear();
        var recarregado = pontos.findById(ponto.getId()).orElseThrow();
        assertThat(recarregado.getCodigoPublico()).isEqualTo("REF-001");
        assertThat(recarregado.getNome()).isEqualTo("Nome atualizado");
    }

    @Test
    @Transactional
    void bancoAceitaOsLimitesInclusivosDasMedicoes() {
        assertThat(jdbc.update("update medicao set temperatura_c=0.0, umidade_pct=0.0")).isEqualTo(10);
        assertThat(jdbc.update("update medicao set temperatura_c=60.0, umidade_pct=100.0")).isEqualTo(10);
    }

    @Test
    @Transactional
    void jpaNaoRemovePontoComHistorico() {
        var ponto = pontos.findByCodigoPublico("REF-001").orElseThrow();
        assertThatThrownBy(() -> {
            pontos.delete(ponto);
            pontos.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest(name = "PostgreSQL rejeita: {0}")
    @MethodSource("violacoes")
    void bancoImpedeDadosInvalidos(String sql) {
        assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
    }

    static Stream<String> violacoes() {
        return Stream.of(
                "update usuario set email='cidadao01@example.test' where email='cidadao02@example.test'",
                "update ponto_refugio set codigo_publico='REF-001' where codigo_publico='REF-002'",
                "update ponto_refugio set codigo_publico='BAD-001' where codigo_publico='REF-002'",
                "update ponto_refugio set nome=' ' where codigo_publico='REF-001'",
                "update ponto_refugio set bairro='' where codigo_publico='REF-001'",
                "update ponto_refugio set horario_texto='' where codigo_publico='REF-001'",
                "insert into recurso_ponto(ponto_id,tipo,disponivel,atualizado_em) select ponto_id,tipo,disponivel,atualizado_em from recurso_ponto limit 1",
                "update recurso_ponto set ponto_id=999999",
                "update medicao set ponto_id=999999",
                "update ocorrencia set ponto_id=999999",
                "update ocorrencia set usuario_id=999999",
                "update recurso_ponto set ponto_id=null",
                "update medicao set ponto_id=null",
                "update ocorrencia set ponto_id=null",
                "update ocorrencia set usuario_id=null",
                "update medicao set temperatura_c=-0.1",
                "update medicao set temperatura_c=60.1",
                "update medicao set temperatura_c=null",
                "update medicao set umidade_pct=-0.1",
                "update medicao set umidade_pct=100.1",
                "update medicao set umidade_pct=null",
                "update ocorrencia set descricao='curta'",
                "update ocorrencia set descricao=repeat('a',501)",
                "update usuario set papel='INVALIDO'",
                "update ponto_refugio set status='INVALIDO'",
                "update recurso_ponto set tipo='INVALIDO'",
                "update medicao set origem='INVALIDO'",
                "update ocorrencia set categoria='INVALIDO'",
                "update ocorrencia set prioridade='X'",
                "update ocorrencia set status='INVALIDO'",
                "delete from ponto_refugio where codigo_publico='REF-001'",
                "delete from usuario where email='cidadao01@example.test'"
        );
    }

    private void conferir(String arquivo, String sql) throws IOException {
        String conteudo = new ClassPathResource("aprovados/" + arquivo + ".txt").getContentAsString(StandardCharsets.UTF_8);
        assertThat(jdbc.queryForList(sql, String.class)).containsExactlyInAnyOrderElementsOf(conteudo.lines().toList());
    }

    private List<List<Map<String, Object>>> snapshot() {
        return Stream.of("usuario", "ponto_refugio", "recurso_ponto", "medicao", "ocorrencia")
                .map(tabela -> jdbc.queryForList("select * from " + tabela + " order by id")).toList();
    }
}
