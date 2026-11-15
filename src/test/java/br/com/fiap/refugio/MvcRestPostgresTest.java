package br.com.fiap.refugio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop", "app.seed.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MvcRestPostgresTest {
    private final MockMvc mvc;
    private String token;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final org.springframework.core.env.Environment environment;
    private final jakarta.persistence.EntityManagerFactory entityManagerFactory;
    private final org.springframework.cache.CacheManager cache;

    MvcRestPostgresTest(MockMvc mvc, ObjectMapper mapper, JdbcTemplate jdbc,
                        org.springframework.core.env.Environment environment,
                        jakarta.persistence.EntityManagerFactory entityManagerFactory,
                        org.springframework.cache.CacheManager cache) {
        this.mvc = mvc;
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.environment = environment;
        this.entityManagerFactory = entityManagerFactory;
        this.cache = cache;
    }

    @org.junit.jupiter.api.BeforeEach
    void limparFixtures() throws Exception {
        cache.getCache("pontoDetalhe").clear();
        jdbc.execute("truncate table ocorrencia, medicao, recurso_ponto, ponto_refugio, usuario restart identity cascade");
        mvc.perform(post("/api/auth/cadastro").contentType("application/json").content("{\"nome\":\"Gestor teste\",\"email\":\"mvc@example.test\",\"senha\":\"SenhaTeste2026!\"}"))
                .andExpect(status().isCreated());
        jdbc.update("update usuario set papel='GESTOR' where email='mvc@example.test'");
        token = mapper.readTree(mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"mvc@example.test\",\"senha\":\"SenhaTeste2026!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registry) {
        BancoTeste.configurar(registry);
    }

    private String pontoJson(String codigo) {
        return """
                {"codigoPublico":"%s","nome":"Refúgio teste","logradouro":"Rua teste","numero":"1",
                "bairro":"Centro","cidade":"São Paulo","uf":"SP","horarioTexto":"08h-18h","status":"ATIVO"}
                """.formatted(codigo);
    }

    private JsonNode criarPonto(String codigo) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/pontos").header("Authorization", "Bearer " + token).contentType("application/json")
                .content(pontoJson(codigo))).andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andReturn().getResponse().getContentAsString());
    }

    @Test
    void consultaAtualizaEExcluiPontoSemFilhos() throws Exception {
        long id = criarPonto("REF-102").get("id").asLong();
        mvc.perform(get("/api/pontos/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.avisoDados").value("Dados demonstrativos. Confirme local e horários antes de usar"))
                .andExpect(jsonPath("$.etiqueta").value("INFORMATIVO"))
                .andExpect(jsonPath("$.textoSemMedicao").value("Sem medição"))
                .andExpect(jsonPath("$.recursos").isEmpty());
        String update = pontoJson("REF-102").replace("\"codigoPublico\":\"REF-102\",", "")
                .replace("Refúgio teste", "Nome novo");
        mvc.perform(put("/api/pontos/{id}", id).header("Authorization", "Bearer " + token).contentType("application/json").content(update))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get("/api/pontos/{id}", id)).andExpect(jsonPath("$.nome").value("Nome novo"))
                .andExpect(jsonPath("$.codigoPublico").value("REF-102"));
        mvc.perform(delete("/api/pontos/{id}", id).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/pontos/{id}", id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.path").value("/api/pontos/" + id));
    }

    @Test
    void listaSomenteAtivosComPaginaEOrdenacaoEstavel() throws Exception {
        criarPonto("REF-103");
        long inativo = criarPonto("REF-104").get("id").asLong();
        jdbc.update("update ponto_refugio set status='INATIVO' where id=?", inativo);
        mvc.perform(get("/api/pontos").param("size", "1").param("sort", "id,desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$._embedded.pontos[0].status").value("ATIVO"))
                .andExpect(jsonPath("$._embedded.pontos[0].recursos").doesNotExist());
        for (String query : new String[]{"page=-1", "size=0", "size=101", "sort=medicoes,asc", "sort=id,banana"}) {
            mvc.perform(get("/api/pontos?" + query)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void rejeitaOffsetAcimaDoLimiteJpaComErroJsonNasDuasListas() throws Exception {
        long pontoId = criarPonto("REF-504").get("id").asLong();
        var estatisticas = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        org.junit.jupiter.api.Assertions.assertAll(
                java.util.stream.Stream.of("/api/pontos", "/api/pontos/" + pontoId + "/medicoes")
                        .map(path -> (org.junit.jupiter.api.function.Executable) () -> {
                            estatisticas.clear();
                            mvc.perform(get(path).param("page", "2147483647").param("size", "100"))
                                    .andExpect(status().isBadRequest())
                                    .andExpect(content().contentType("application/json"))
                                    .andExpect(jsonPath("$.status").value(400))
                                    .andExpect(jsonPath("$.erro").value("Bad Request"))
                                    .andExpect(jsonPath("$.mensagem").value("O offset (page * size) deve ser no máximo 2147483647"))
                                    .andExpect(jsonPath("$.path").value(path))
                                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
                            assertThat(estatisticas.getPrepareStatementCount()).isZero();
                        }));
    }

    @Test
    void aceitaOffsetNoLimiteJpaComPaginaVaziaNasDuasListas() throws Exception {
        long pontoId = criarPonto("REF-505").get("id").asLong();
        for (String path : new String[]{"/api/pontos", "/api/pontos/" + pontoId + "/medicoes"}) {
            mvc.perform(get(path).param("page", "2147483647").param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$._embedded").doesNotExist());
        }
    }

    @Test
    void validaPontoERejeitaCodigoNoUpdate() throws Exception {
        long id = criarPonto("REF-105").get("id").asLong();
        mvc.perform(post("/api/pontos").header("Authorization", "Bearer " + token).contentType("application/json").content(pontoJson("REF-105")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        mvc.perform(put("/api/pontos/{id}", id).header("Authorization", "Bearer " + token).contentType("application/json").content(pontoJson("REF-999")))
                .andExpect(status().isBadRequest());
        for (String body : new String[]{"{}", "{", pontoJson("BAD-001"), pontoJson("REF-106").replace("Refúgio teste", " "), pontoJson("REF-106").replace("ATIVO", "INVALIDO")}) {
            mvc.perform(post("/api/pontos").header("Authorization", "Bearer " + token).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").exists())
                    .andExpect(jsonPath("$.mensagem").exists()).andExpect(jsonPath("$.timestamp").exists());
        }
        mvc.perform(get("/api/pontos/abc")).andExpect(status().isBadRequest());
    }

    private String recursoJson(long pontoId, String tipo, String disponivel) {
        return "{\"pontoId\":" + pontoId + ",\"tipo\":\"" + tipo + "\",\"disponivel\":" + disponivel + "}";
    }

    @Test
    void listaRecursosEmUmaConsultaSemCarregarMedicoesEOrdenaPorId() throws Exception {
        long pontoId = criarPonto("REF-204").get("id").asLong();
        long outro = criarPonto("REF-205").get("id").asLong();
        criarMedicao(pontoId, "32", "50", "2026-11-09T09:00:00Z");
        jdbc.update("insert into recurso_ponto(id,ponto_id,tipo,disponivel,atualizado_em) values (30,?,'SOMBRA',true,now()),(10,?,'AGUA',false,now()),(20,?,'ASSENTO',true,now()),(5,?,'AGUA',true,now())",
                pontoId, pontoId, pontoId, outro);
        var estatisticas = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        estatisticas.clear();
        mvc.perform(get("/api/pontos/{id}/recursos", pontoId)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/hal+json"))
                .andExpect(jsonPath("$._embedded.recursos.length()").value(3))
                .andExpect(jsonPath("$._embedded.recursos[0].id").value(10))
                .andExpect(jsonPath("$._embedded.recursos[1].id").value(20))
                .andExpect(jsonPath("$._embedded.recursos[2].id").value(30))
                .andExpect(jsonPath("$._embedded.recursos[0].pontoId").value(pontoId))
                .andExpect(jsonPath("$._embedded.recursos[0].tipo").value("AGUA"))
                .andExpect(jsonPath("$._embedded.recursos[0].disponivel").value(false))
                .andExpect(jsonPath("$._embedded.recursos[0].atualizadoEm").isNotEmpty())
                .andExpect(jsonPath("$._embedded.recursos[0].ponto").doesNotExist())
                .andExpect(jsonPath("$._embedded.recursos[0]._links.ponto.href").value("http://localhost/api/pontos/" + pontoId))
                .andExpect(jsonPath("$._links.self.href").value("http://localhost/api/pontos/" + pontoId + "/recursos"));
        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertThat(estatisticas.getPrepareStatementCount()).isOne(),
                () -> assertThat(estatisticas.getEntityStatistics("br.com.fiap.refugio.model.PontoRefugio").getLoadCount()).isOne(),
                () -> assertThat(estatisticas.getEntityStatistics("br.com.fiap.refugio.model.RecursoPonto").getLoadCount()).isEqualTo(3),
                () -> assertThat(estatisticas.getEntityStatistics("br.com.fiap.refugio.model.Medicao").getLoadCount()).isZero(),
                () -> assertThat(estatisticas.getEntityStatistics("br.com.fiap.refugio.model.Usuario").getLoadCount()).isZero());
    }

    @Test
    void atualizaRecursoNoMesmoPaiSemCarregaLoEInvalidaDetalhe() throws Exception {
        long pontoId = criarPonto("REF-206").get("id").asLong();
        JsonNode recurso = mapper.readTree(mvc.perform(post("/api/recursos").header("Authorization", "Bearer " + token).contentType("application/json")
                .content(recursoJson(pontoId, "AGUA", "false"))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long id = recurso.get("id").asLong();
        mvc.perform(get("/api/pontos/{id}", pontoId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.recursos[0].disponivel").value(false));
        var estatisticas = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        estatisticas.clear();
        mvc.perform(get("/api/pontos/{id}", pontoId)).andExpect(status().isOk());
        assertThat(estatisticas.getPrepareStatementCount()).isZero();
        estatisticas.clear();
        mvc.perform(put("/api/recursos/{id}", id).header("Authorization", "Bearer " + token).contentType("application/json")
                .content(recursoJson(pontoId, "SOMBRA", "true"))).andExpect(status().isNoContent())
                .andExpect(content().string(""));
        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(3),
                () -> assertThat(estatisticas.getEntityStatistics("br.com.fiap.refugio.model.Usuario").getLoadCount()).isOne(),
                () -> assertThat(estatisticas.getEntityStatistics("br.com.fiap.refugio.model.RecursoPonto").getLoadCount()).isOne(),
                () -> assertThat(estatisticas.getEntityStatistics("br.com.fiap.refugio.model.PontoRefugio").getLoadCount()).isZero(),
                () -> assertThat(estatisticas.getEntityUpdateCount()).isOne());
        estatisticas.clear();
        JsonNode atualizado = mapper.readTree(mvc.perform(get("/api/pontos/{id}", pontoId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.recursos[0].id").value(id))
                .andExpect(jsonPath("$.recursos[0].pontoId").value(pontoId))
                .andExpect(jsonPath("$.recursos[0].tipo").value("SOMBRA"))
                .andExpect(jsonPath("$.recursos[0].disponivel").value(true))
                .andReturn().getResponse().getContentAsString());
        assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(2);
        assertThat(java.time.Instant.parse(atualizado.get("recursos").get(0).get("atualizadoEm").asText()))
                .isAfter(java.time.Instant.parse(recurso.get("atualizadoEm").asText()));
        estatisticas.clear();
        mvc.perform(get("/api/pontos/{id}", pontoId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.recursos[0].disponivel").value(true));
        assertThat(estatisticas.getPrepareStatementCount()).isZero();
    }

    @Test
    void recursoCrudPermiteTrocarPaiEProtegeUnicidade() throws Exception {
        long primeiro = criarPonto("REF-201").get("id").asLong();
        long segundo = criarPonto("REF-202").get("id").asLong();
        JsonNode recurso = mapper.readTree(mvc.perform(post("/api/recursos").header("Authorization", "Bearer " + token).contentType("application/json")
                .content(recursoJson(primeiro, "AGUA", "false"))).andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/pontos/" + primeiro + "/recursos"))
                .andExpect(jsonPath("$.disponivel").value(false)).andReturn().getResponse().getContentAsString());
        long id = recurso.get("id").asLong();
        mvc.perform(get("/api/pontos/{id}/recursos", primeiro)).andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.recursos[0].id").value(id)).andExpect(jsonPath("$._embedded.recursos[0].ponto").doesNotExist());
        mvc.perform(delete("/api/pontos/{id}", primeiro).header("Authorization", "Bearer " + token)).andExpect(status().isConflict());
        mvc.perform(post("/api/recursos").header("Authorization", "Bearer " + token).contentType("application/json").content(recursoJson(segundo, "SOMBRA", "true")))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/pontos/{id}", primeiro)).andExpect(status().isOk());
        mvc.perform(get("/api/pontos/{id}", segundo)).andExpect(status().isOk());
        var antes = jdbc.queryForMap("select * from recurso_ponto where id=?", id);
        mvc.perform(put("/api/recursos/{id}", id).header("Authorization", "Bearer " + token).contentType("application/json").content(recursoJson(segundo, "SOMBRA", "false")))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForMap("select * from recurso_ponto where id=?", id)).isEqualTo(antes);
        var estatisticas = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        estatisticas.clear();
        mvc.perform(get("/api/pontos/{id}", primeiro)).andExpect(status().isOk())
                .andExpect(jsonPath("$.recursos[0].tipo").value("AGUA"))
                .andExpect(jsonPath("$.recursos[0].disponivel").value(false));
        mvc.perform(get("/api/pontos/{id}", segundo)).andExpect(status().isOk())
                .andExpect(jsonPath("$.recursos.length()").value(1));
        assertThat(estatisticas.getPrepareStatementCount()).isZero();
        estatisticas.clear();
        mvc.perform(put("/api/recursos/{id}", id).header("Authorization", "Bearer " + token).contentType("application/json").content(recursoJson(segundo, "AGUA", "true")))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(4);
        assertThat(estatisticas.getEntityStatistics("br.com.fiap.refugio.model.Usuario").getLoadCount()).isOne();
        assertThat(estatisticas.getEntityStatistics("br.com.fiap.refugio.model.PontoRefugio").getLoadCount()).isOne();
        assertThat(estatisticas.getEntityUpdateCount()).isOne();
        estatisticas.clear();
        mvc.perform(get("/api/pontos/{id}", primeiro)).andExpect(status().isOk())
                .andExpect(jsonPath("$.recursos").isEmpty());
        mvc.perform(get("/api/pontos/{id}", segundo)).andExpect(status().isOk())
                .andExpect(jsonPath("$.recursos.length()").value(2));
        assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(4);
        mvc.perform(get("/api/pontos/{id}/recursos", primeiro)).andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded").doesNotExist());
        mvc.perform(delete("/api/recursos/{id}", id).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/pontos/{id}", segundo)).andExpect(status().isOk())
                .andExpect(jsonPath("$.recursos.length()").value(1))
                .andExpect(jsonPath("$.recursos[0].tipo").value("SOMBRA"));
        mvc.perform(delete("/api/recursos/{id}", id).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
    }

    @Test
    void validaRecursoENaoConfundePaiAusenteComListaVazia() throws Exception {
        long pontoId = criarPonto("REF-203").get("id").asLong();
        var estatisticas = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        estatisticas.clear();
        mvc.perform(get("/api/pontos/{id}/recursos", pontoId)).andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded").doesNotExist())
                .andExpect(jsonPath("$._links.self.href").value("http://localhost/api/pontos/" + pontoId + "/recursos"));
        assertThat(estatisticas.getPrepareStatementCount()).isOne();
        estatisticas.clear();
        mvc.perform(get("/api/pontos/999999/recursos")).andExpect(status().isNotFound())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensagem").value("Ponto não encontrado"))
                .andExpect(jsonPath("$.path").value("/api/pontos/999999/recursos"));
        assertThat(estatisticas.getPrepareStatementCount()).isOne();
        for (String body : new String[]{"{}", recursoJson(pontoId, "AGUA", "null"), recursoJson(pontoId, "INVALIDO", "true")}) {
            mvc.perform(post("/api/recursos").header("Authorization", "Bearer " + token).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/recursos").header("Authorization", "Bearer " + token).contentType("application/json").content(recursoJson(999999, "AGUA", "true")))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/recursos/999999").header("Authorization", "Bearer " + token).contentType("application/json").content(recursoJson(pontoId, "AGUA", "true")))
                .andExpect(status().isNotFound());
        long id = mapper.readTree(mvc.perform(post("/api/recursos").header("Authorization", "Bearer " + token).contentType("application/json").content(recursoJson(pontoId, "AGUA", "true")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/recursos").header("Authorization", "Bearer " + token).contentType("application/json").content(recursoJson(pontoId, "AGUA", "false")))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/pontos/{id}", pontoId)).andExpect(status().isOk());
        var antes = jdbc.queryForMap("select * from recurso_ponto where id=?", id);
        estatisticas.clear();
        mvc.perform(put("/api/recursos/{id}", id).header("Authorization", "Bearer " + token).contentType("application/json")
                .content(recursoJson(999999, "SOMBRA", "false"))).andExpect(status().isNotFound())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensagem").value("Ponto não encontrado"))
                .andExpect(jsonPath("$.path").value("/api/recursos/" + id));
        assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(3);
        assertThat(estatisticas.getEntityStatistics("br.com.fiap.refugio.model.Usuario").getLoadCount()).isOne();
        assertThat(estatisticas.getEntityUpdateCount()).isZero();
        assertThat(jdbc.queryForMap("select * from recurso_ponto where id=?", id)).isEqualTo(antes);
        estatisticas.clear();
        mvc.perform(get("/api/pontos/{id}", pontoId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.recursos[0].pontoId").value(pontoId))
                .andExpect(jsonPath("$.recursos[0].tipo").value("AGUA"))
                .andExpect(jsonPath("$.recursos[0].disponivel").value(true));
        assertThat(estatisticas.getPrepareStatementCount()).isZero();
    }

    private String medicaoJson(long pontoId, String temperatura, String umidade, String instante) {
        return "{\"pontoId\":" + pontoId + ",\"temperaturaC\":" + temperatura + ",\"umidadePct\":" + umidade
                + ",\"origem\":\"SIMULADA\",\"medidaEm\":\"" + instante + "\"}";
    }

    private long criarMedicao(long pontoId, String temperatura, String umidade, String instante) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/medicoes").header("Authorization", "Bearer " + token).contentType("application/json")
                .content(medicaoJson(pontoId, temperatura, umidade, instante))).andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/pontos/" + pontoId + "/medicoes"))
                .andExpect(jsonPath("$.ponto").doesNotExist()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void registraMedicaoEUsaUltimaLeituraParaEtiquetaDidatica() throws Exception {
        long pontoId = criarPonto("REF-301").get("id").asLong();
        criarMedicao(pontoId, "60.0", "100.0", "2026-11-09T08:00:00Z");
        criarMedicao(pontoId, "31.9", "0.0", "2026-11-09T09:00:00Z");
        mvc.perform(get("/api/pontos/{id}", pontoId)).andExpect(jsonPath("$.etiqueta").value("INFORMATIVO"))
                .andExpect(jsonPath("$.ultimaMedicao.temperaturaC").value(31.9))
                .andExpect(jsonPath("$.textoSemMedicao").isEmpty());
        long ultima = criarMedicao(pontoId, "32.0", "40.0", "2026-11-09T09:00:00Z");
        mvc.perform(get("/api/pontos/{id}", pontoId)).andExpect(jsonPath("$.etiqueta").value("ATENCAO_DIDATICA"))
                .andExpect(jsonPath("$.ultimaMedicao.id").value(ultima))
                .andExpect(jsonPath("$.ultimaMedicao.medidaEm").value("2026-11-09T09:00:00Z"));
        mvc.perform(delete("/api/pontos/{id}", pontoId).header("Authorization", "Bearer " + token)).andExpect(status().isConflict());
    }

    @Test
    void paginaMedicoesSemPerderEmpatesEPreservaIdDescExplicito() throws Exception {
        long pontoId = criarPonto("REF-302").get("id").asLong();
        long antigo = criarMedicao(pontoId, "0.0", "100.0", "2026-11-09T08:00:00Z");
        long primeiro = criarMedicao(pontoId, "20.0", "50.0", "2026-11-09T09:00:00Z");
        long segundo = criarMedicao(pontoId, "21.0", "50.0", "2026-11-09T09:00:00Z");
        var encontrados = new java.util.ArrayList<Long>();
        for (int page = 0; page < 3; page++) {
            JsonNode pagina = mapper.readTree(mvc.perform(get("/api/pontos/{id}/medicoes", pontoId)
                    .param("page", String.valueOf(page)).param("size", "1")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.totalElements").value(3)).andExpect(jsonPath("$.page.totalPages").value(3))
                    .andReturn().getResponse().getContentAsString());
            encontrados.add(pagina.get("_embedded").get("medicoes").get(0).get("id").asLong());
        }
        assertThat(encontrados).containsExactly(segundo, primeiro, antigo);
        mvc.perform(get("/api/pontos/{id}/medicoes", pontoId).param("sort", "id,desc"))
                .andExpect(jsonPath("$._embedded.medicoes[0].id").value(segundo));
        mvc.perform(get("/api/pontos/{id}/medicoes", pontoId).param("page", "3").param("size", "1"))
                .andExpect(jsonPath("$._embedded").doesNotExist());
    }

    @Test
    void validaLimitesMedicaoEPaiExistente() throws Exception {
        long pontoId = criarPonto("REF-303").get("id").asLong();
        mvc.perform(get("/api/pontos/{id}/medicoes", pontoId)).andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(0));
        mvc.perform(get("/api/pontos/999999/medicoes")).andExpect(status().isNotFound());
        for (String[] valores : new String[][]{{"-0.1", "50"}, {"60.1", "50"}, {"30", "-0.1"}, {"30", "100.1"}, {"30.01", "50"}, {"null", "50"}, {"30", "null"}}) {
            mvc.perform(post("/api/medicoes").header("Authorization", "Bearer " + token).contentType("application/json")
                    .content(medicaoJson(pontoId, valores[0], valores[1], "2026-11-09T09:00:00Z")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        mvc.perform(post("/api/medicoes").header("Authorization", "Bearer " + token).contentType("application/json")
                .content(medicaoJson(999999, "30", "50", "2026-11-09T09:00:00Z"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/medicoes").header("Authorization", "Bearer " + token).contentType("application/json")
                .content(medicaoJson(pontoId, "30", "50", "inválido"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/pontos/{id}/medicoes", pontoId).param("sort", "ponto.nome,asc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/pontos/{id}/medicoes", pontoId).param("size", "101")).andExpect(status().isBadRequest());
    }

    @Test
    void rn08BloqueiaPontoComApenasOcorrencia() throws Exception {
        long pontoId = criarPonto("REF-401").get("id").asLong();
        Long usuarioId = jdbc.queryForObject("insert into usuario(nome,email,senha_hash,papel,ativo,criado_em) values ('Teste','teste@example.test','hash','CIDADAO',true,now()) returning id", Long.class);
        jdbc.update("insert into ocorrencia(ponto_id,usuario_id,categoria,descricao,prioridade,status,aberta_em) values (?,?,'OUTRO','Relato demonstrativo','BAIXA','ABERTA',now())", pontoId, usuarioId);
        String body = mvc.perform(delete("/api/pontos/{id}", pontoId).header("Authorization", "Bearer " + token)).andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("SQL", "constraint", "hibernate", "ponto_refugio", "senha");
        assertThat(jdbc.queryForObject("select count(*) from ponto_refugio where id=?", Integer.class, pontoId)).isOne();
    }

    @Test
    void preservaErrosMvcConhecidosComJsonExplicito() throws Exception {
        mvc.perform(patch("/api/pontos/1").header("Authorization", "Bearer " + token)).andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType("application/json")).andExpect(jsonPath("$.status").value(405));
        mvc.perform(post("/api/pontos").header("Authorization", "Bearer " + token).contentType("text/plain").content("teste"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.status").value(415));
        mvc.perform(get("/api/pontos").accept("application/xml")).andExpect(status().isNotAcceptable())
                .andExpect(content().contentType("application/json")).andExpect(jsonPath("$.status").value(406));
        for (String path : new String[]{"/api/pontos/1/inexistente", "/api/pontos/"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        }
    }

    @Test
    void servidorRealRespondePublicamenteERejeitaEscritaAnonima() throws Exception {
        long pontoId = criarPonto("REF-402").get("id").asLong();
        String base = "http://127.0.0.1:" + environment.getRequiredProperty("local.server.port");
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            for (String path : new String[]{"/api/pontos", "/api/pontos/" + pontoId, "/api/pontos/" + pontoId + "/recursos", "/api/pontos/" + pontoId + "/medicoes"}) {
                var response = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + path))
                        .header("Authorization", "Bearer " + token).header("Accept", "application/json").GET().build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(mapper.readTree(response.body())).isNotNull();
            }
            var ausente = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/api/pontos/1/nao-existe"))
                    .header("Authorization", "Bearer " + token).header("Accept", "application/json").GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(ausente.statusCode()).isEqualTo(404);
            assertThat(mapper.readTree(ausente.body()).get("status").asInt()).isEqualTo(404);
            var anonimo = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/api/pontos"))
                    .header("Accept", "application/json").POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(anonimo.statusCode()).isEqualTo(401);
        }
    }

    @Test
    void detalheFazDuasConsultasEListaNaoCarregaColecoesPorElemento() throws Exception {
        long pontoId = criarPonto("REF-403").get("id").asLong();
        criarPonto("REF-404");
        criarMedicao(pontoId, "32", "50", "2026-11-09T09:00:00Z");
        mvc.perform(post("/api/recursos").header("Authorization", "Bearer " + token).contentType("application/json").content(recursoJson(pontoId, "AGUA", "true")))
                .andExpect(status().isCreated());
        var estatisticas = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        estatisticas.clear();
        mvc.perform(get("/api/pontos/{id}", pontoId)).andExpect(status().isOk());
        assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(2);
        estatisticas.clear();
        mvc.perform(get("/api/pontos")).andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(2));
        assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }

    @Test
    void validaTodosOsCamposDeEnderecoEHorasNoPayload() throws Exception {
        for (String campo : new String[]{"nome", "logradouro", "numero", "bairro", "cidade", "uf", "horarioTexto", "status"}) {
            var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(pontoJson("REF-501"));
            body.putNull(campo);
            mvc.perform(post("/api/pontos").header("Authorization", "Bearer " + token).contentType("application/json").content(mapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
        var limites = java.util.Map.of("nome", 110, "logradouro", 120, "numero", 12, "bairro", 80,
                "cidade", 80, "uf", 2, "horarioTexto", 80);
        for (var limite : limites.entrySet()) {
            var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(pontoJson("REF-501"));
            body.put(limite.getKey(), "x".repeat(limite.getValue() + 1));
            mvc.perform(post("/api/pontos").header("Authorization", "Bearer " + token).contentType("application/json").content(mapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void aceitaTamanhoMaximoEDefaultsComSortMultiplo() throws Exception {
        long primeiro = criarPonto("REF-502").get("id").asLong();
        long segundo = criarPonto("REF-503").get("id").asLong();
        mvc.perform(get("/api/pontos")).andExpect(jsonPath("$.page.number").value(0)).andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(2)).andExpect(jsonPath("$._embedded.pontos[0].id").value(primeiro));
        mvc.perform(get("/api/pontos").param("size", "100").param("sort", "nome,asc", "id,desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.size").value(100))
                .andExpect(jsonPath("$._embedded.pontos[0].id").value(segundo));
        for (String valor : new String[]{"", "id,", "id,asc,desc"}) {
            mvc.perform(get("/api/pontos").param("sort", valor)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/pontos").param("sort", "id,asc", "id,desc")).andExpect(status().isBadRequest());
    }

    @Test
    void criaPontoComDtoELocation() throws Exception {
        JsonNode ponto = criarPonto("REF-101");
        assertThat(ponto.get("codigoPublico").asText()).isEqualTo("REF-101");
        assertThat(ponto.has("id")).isTrue();
        assertThat(ponto.has("recursos")).isFalse();
        assertThat(jdbc.queryForObject("select nome from ponto_refugio where id=?", String.class, ponto.get("id").asLong()))
                .isEqualTo("Refúgio teste");
    }
}
