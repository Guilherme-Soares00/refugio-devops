package br.com.fiap.refugio;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.core.env.Environment;
import java.net.URI;
import java.net.http.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop", "app.seed.enabled=false"})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FinalHttpPostgresTest {
    private final Environment environment;
    private final ObjectMapper mapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final jakarta.persistence.EntityManagerFactory emf;
    private final org.springframework.cache.CacheManager cache;

    FinalHttpPostgresTest(Environment environment, ObjectMapper mapper, org.springframework.jdbc.core.JdbcTemplate jdbc,
                          jakarta.persistence.EntityManagerFactory emf, org.springframework.cache.CacheManager cache) {
        this.environment = environment;
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.emf = emf;
        this.cache = cache;
    }

    @org.junit.jupiter.api.BeforeEach
    void limpar() {
        jdbc.execute("truncate table ocorrencia,medicao,recurso_ponto,ponto_refugio,usuario restart identity cascade");
        cache.getCache("pontoDetalhe").clear();
    }

    private HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + environment.getRequiredProperty("local.server.port") + path))
                .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try (var client = HttpClient.newHttpClient()) { return client.send(builder.build(), HttpResponse.BodyHandlers.ofString()); }
    }

    private String cadastrarLogin(String email, boolean gestor) throws Exception {
        assertThat(request("POST", "/api/auth/cadastro", "{\"nome\":\"Pessoa teste\",\"email\":\"" + email + "\",\"senha\":\"SenhaTeste2026!\"}", null).statusCode()).isEqualTo(201);
        if (gestor) jdbc.update("update usuario set papel='GESTOR' where email=?", email);
        var login = request("POST", "/api/auth/login", "{\"email\":\"" + email + "\",\"senha\":\"SenhaTeste2026!\"}", null);
        assertThat(login.statusCode()).isEqualTo(200);
        return mapper.readTree(login.body()).get("accessToken").asText();
    }

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registry) { BancoTeste.configurar(registry); }

    @Test
    void actuatorESwaggerPublicosSemSegredosComBearerDocumentado() throws Exception {
        var info = request("GET", "/actuator/info", null, null);
        assertThat(info.statusCode()).isEqualTo(200);
        assertThat(info.body()).contains("Dados demonstrativos. Confirme local e horários antes de usar");
        assertThat(request("GET", "/actuator/health", null, null).statusCode()).isEqualTo(200);
        assertThat(request("GET", "/actuator/metrics", null, null).statusCode()).isEqualTo(401);
        var docs = request("GET", "/v3/api-docs", null, null);
        assertThat(docs.statusCode()).isEqualTo(200);
        var schema = mapper.readTree(docs.body());
        assertThat(schema.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText()).isEqualTo("bearer");
        assertThat(schema.path("info").path("description").asText()).contains("Dados demonstrativos");
        assertThat(schema.path("paths").path("/api/ocorrencias/{id}/status").path("patch").path("responses").has("409")).isTrue();
    }

    @Test
    void detalheTemLinksDinamicosECacheNaoRepeteConsulta() throws Exception {
        String gestor = cadastrarLogin("cache@example.test", true);
        Long ponto = jdbc.queryForObject("insert into ponto_refugio(codigo_publico,nome,logradouro,numero,bairro,cidade,uf,horario_texto,status,cadastrado_em) values ('REF-801','Cache','Rua','1','Centro','Cidade','SP','08h','ATIVO',now()) returning id", Long.class);
        var detalhe = mapper.readTree(request("GET", "/api/pontos/" + ponto, null, null).body());
        assertThat(detalhe.path("_links").path("self").path("href").asText()).endsWith("/api/pontos/" + ponto);
        assertThat(detalhe.path("_links").has("atualizar")).isFalse();
        assertThat(mapper.readTree(request("GET", "/api/pontos/" + ponto, null, gestor).body()).path("_links").has("atualizar")).isTrue();
        var pagina = mapper.readTree(request("GET", "/api/pontos?size=1", null, null).body());
        assertThat(pagina.has("page")).isTrue();
        assertThat(pagina.has("_links")).isTrue();
        assertThat(pagina.has("_embedded")).isTrue();
    }

    @Test
    void criaOcorrenciaComPrioridadeDoServidorETransicaoSequencial() throws Exception {
        String cidadao = cadastrarLogin("relator@example.test", false);
        String gestor = cadastrarLogin("gestor@example.test", true);
        Long ponto = jdbc.queryForObject("insert into ponto_refugio(codigo_publico,nome,logradouro,numero,bairro,cidade,uf,horario_texto,status,cadastrado_em) values ('REF-800','Ponto','Rua','1','Centro','Cidade','SP','08h','ATIVO',now()) returning id", Long.class);
        var criada = request("POST", "/api/ocorrencias", "{\"pontoId\":" + ponto + ",\"categoria\":\"ACESSO_BLOQUEADO\",\"descricao\":\"Acesso bloqueado teste\",\"prioridade\":\"BAIXA\",\"status\":\"RESOLVIDA\",\"usuarioId\":99999}", cidadao);
        assertThat(criada.statusCode()).isEqualTo(201);
        var json = mapper.readTree(criada.body());
        assertThat(json.get("prioridade").asText()).isEqualTo("ALTA");
        assertThat(json.get("status").asText()).isEqualTo("ABERTA");
        String path = "/api/ocorrencias/" + json.get("id").asLong() + "/status";
        assertThat(request("PATCH", path, "{\"status\":\"EM_ANALISE\"}", cidadao).statusCode()).isEqualTo(403);
        assertThat(request("PATCH", path, "{\"status\":\"RESOLVIDA\"}", gestor).statusCode()).isEqualTo(409);
        assertThat(request("PATCH", path, "{\"status\":\"EM_ANALISE\"}", gestor).statusCode()).isEqualTo(204);
        assertThat(request("PATCH", path, "{\"status\":\"RESOLVIDA\"}", gestor).statusCode()).isEqualTo(204);
        assertThat(request("PATCH", path, "{\"status\":\"ABERTA\"}", gestor).statusCode()).isEqualTo(409);
        assertThat(request("GET", "/api/ocorrencias/minhas", null, cidadao).body()).contains("RESOLVIDA", "resolvidaEm");
        assertThat(request("GET", "/api/ocorrencias/minhas", null, gestor).body()).doesNotContain("Acesso bloqueado teste");
    }

    @Test
    void transicoesConcorrentesDaMesmaOcorrenciaTemUmaVencedora() throws Exception {
        String gestor = cadastrarLogin("concorrencia@example.test", true);
        long id = criarOcorrencia(gestor);
        String path = "/api/ocorrencias/" + id + "/status";
        var stats = emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean habilitadas = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        stats.clear();
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            try (var connection = java.sql.DriverManager.getConnection(BancoTeste.url(), BancoTeste.usuario(), BancoTeste.senha())) {
                connection.setAutoCommit(false);
                try {
                    bloquearOcorrencia(connection, id);
                    var primeira = executor.submit(() -> request("PATCH", path, "{\"status\":\"EM_ANALISE\"}", gestor));
                    var segunda = executor.submit(() -> request("PATCH", path, "{\"status\":\"EM_ANALISE\"}", gestor));
                    aguardarRequisicoesBloqueadas(2, stats);
                    assertThat(primeira.isDone()).isFalse();
                    assertThat(segunda.isDone()).isFalse();
                    connection.commit();
                    assertThat(java.util.List.of(primeira.get(15, java.util.concurrent.TimeUnit.SECONDS).statusCode(),
                            segunda.get(15, java.util.concurrent.TimeUnit.SECONDS).statusCode()))
                            .containsExactlyInAnyOrder(204, 409);
                } finally {
                    connection.rollback();
                }
            }
        } finally {
            stats.setStatisticsEnabled(habilitadas);
        }
        assertThat(request("PATCH", path, "{\"status\":\"RESOLVIDA\"}", gestor).statusCode()).isEqualTo(204);
        var resolvidaEm = jdbc.queryForObject("select resolvida_em from ocorrencia where id=?", java.time.OffsetDateTime.class, id);
        assertThat(resolvidaEm).isNotNull();
        assertThat(request("PATCH", path, "{\"status\":\"EM_ANALISE\"}", gestor).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select status from ocorrencia where id=?", String.class, id)).isEqualTo("RESOLVIDA");
        assertThat(jdbc.queryForObject("select resolvida_em from ocorrencia where id=?", java.time.OffsetDateTime.class, id)).isEqualTo(resolvidaEm);
    }

    @Test
    void transicaoBloqueadaNaoSobrescreveOcorrenciaJaResolvida() throws Exception {
        String gestor = cadastrarLogin("stale@example.test", true);
        long id = criarOcorrencia(gestor);
        var stats = emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean habilitadas = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        stats.clear();
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            try (var connection = java.sql.DriverManager.getConnection(BancoTeste.url(), BancoTeste.usuario(), BancoTeste.senha())) {
                connection.setAutoCommit(false);
                try {
                    bloquearOcorrencia(connection, id);
                    var pendente = executor.submit(() -> request("PATCH", "/api/ocorrencias/" + id + "/status", "{\"status\":\"EM_ANALISE\"}", gestor));
                    aguardarRequisicoesBloqueadas(1, stats);
                    assertThat(pendente.isDone()).isFalse();
                    try (var update = connection.prepareStatement("update ocorrencia set status='RESOLVIDA',resolvida_em='2026-11-09T12:00:00Z' where id=?")) {
                        update.setLong(1, id);
                        assertThat(update.executeUpdate()).isEqualTo(1);
                    }
                    connection.commit();
                    assertThat(pendente.get(15, java.util.concurrent.TimeUnit.SECONDS).statusCode()).isEqualTo(409);
                } finally {
                    connection.rollback();
                }
            }
        } finally {
            stats.setStatisticsEnabled(habilitadas);
        }
        assertThat(jdbc.queryForObject("select status from ocorrencia where id=?", String.class, id)).isEqualTo("RESOLVIDA");
        assertThat(jdbc.queryForObject("select resolvida_em from ocorrencia where id=?", java.time.OffsetDateTime.class, id).toInstant())
                .isEqualTo(java.time.Instant.parse("2026-11-09T12:00:00Z"));
    }

    private long criarOcorrencia(String token) throws Exception {
        long ponto = ponto("REF-950", "Centro", "ATIVO");
        var resposta = request("POST", "/api/ocorrencias", "{\"pontoId\":" + ponto + ",\"categoria\":\"OUTRO\",\"descricao\":\"Relato concorrente\"}", token);
        assertThat(resposta.statusCode()).isEqualTo(201);
        return mapper.readTree(resposta.body()).path("id").asLong();
    }

    private void bloquearOcorrencia(java.sql.Connection connection, long id) throws Exception {
        try (var statement = connection.prepareStatement("select id from ocorrencia where id=? for update")) {
            statement.setLong(1, id);
            try (var resultado = statement.executeQuery()) {
                assertThat(resultado.next()).isTrue();
            }
        }
    }

    private void aguardarRequisicoesBloqueadas(int quantidade, org.hibernate.stat.Statistics stats) throws Exception {
        long limite = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
        java.util.List<String> bloqueadas;
        do {
            bloqueadas = jdbc.queryForList("select query from pg_stat_activity where datname=current_database() and pid<>pg_backend_pid() and state='active' and wait_event_type='Lock' and lower(query) like '%ocorrencia%'", String.class);
            if (bloqueadas.size() == quantidade && stats.getPrepareStatementCount() >= quantidade * 2L) {
                System.out.println("Prova concorrente: prepared=" + stats.getPrepareStatementCount() + ", entityLoads=" + stats.getEntityLoadCount() + ", bloqueadas=" + bloqueadas);
                return;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < limite);
        throw new AssertionError("Requisições não chegaram ao lock: prepared=" + stats.getPrepareStatementCount() + ", bloqueadas=" + bloqueadas);
    }

    @Test
    void ocorrenciasExigemJwtEMantemHistorico() throws Exception {
        String base = "http://127.0.0.1:" + environment.getRequiredProperty("local.server.port");
        try (var client = HttpClient.newHttpClient()) {
            var resposta = client.send(HttpRequest.newBuilder(URI.create(base + "/api/ocorrencias/minhas"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(resposta.statusCode()).isEqualTo(401);
            assertThat(mapper.readTree(resposta.body()).get("status").asInt()).isEqualTo(401);
        }
    }

    @Test
    void buscaPublicaCombinaFiltros() throws Exception {
        String base = "http://127.0.0.1:" + environment.getRequiredProperty("local.server.port");
        try (var client = HttpClient.newHttpClient()) {
            var resposta = client.send(HttpRequest.newBuilder(URI.create(base + "/api/pontos/busca?bairro=centro&tipoRecurso=AGUA"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(resposta.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(resposta.body()).get("page").get("totalElements").asLong()).isZero();
        }
    }

    @Test
    void cadastroPublicoCriaCidadaoELoginEmiteBearer() throws Exception {
        String base = "http://127.0.0.1:" + environment.getRequiredProperty("local.server.port");
        try (var client = HttpClient.newHttpClient()) {
            var cadastro = client.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/cadastro"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(
                            "{\"nome\":\"Teste final\",\"email\":\"final@example.test\",\"senha\":\"SenhaTeste2026!\",\"papel\":\"GESTOR\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(cadastro.statusCode()).isEqualTo(201);
            assertThat(mapper.readTree(cadastro.body()).get("papel").asText()).isEqualTo("CIDADAO");
            assertThat(cadastro.body()).doesNotContain("senha", "Hash");
            var login = client.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(
                            "{\"email\":\"final@example.test\",\"senha\":\"SenhaTeste2026!\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(login.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(login.body()).get("tipo").asText()).isEqualTo("Bearer");
            assertThat(mapper.readTree(login.body()).get("accessToken").asText().split("\\.")).hasSize(3);
        }
    }
    private long ponto(String codigo, String bairro, String status) {
        return jdbc.queryForObject("insert into ponto_refugio(codigo_publico,nome,logradouro,numero,bairro,cidade,uf,horario_texto,status,cadastrado_em) values (?,?,'Rua','1',?,'Cidade','SP','08h',?,now()) returning id", Long.class,codigo,codigo,bairro,status);
    }

    private void recurso(long ponto, String tipo, boolean disponivel) {
        jdbc.update("insert into recurso_ponto(ponto_id,tipo,disponivel,atualizado_em) values (?,?,?,now())",ponto,tipo,disponivel);
    }

    @Test
    void filtrosIndividuaisCombinadosEDuplicatasUsamSomenteRecursosDisponiveis() throws Exception {
        long ativo=ponto("REF-901","Centro","ATIVO");
        long indisponivel=ponto("REF-902","Centro","ATIVO");
        long outro=ponto("REF-903","Outro","ATIVO");
        long inativo=ponto("REF-904","Centro","INATIVO");
        recurso(ativo,"AGUA",true); recurso(ativo,"SOMBRA",true);
        recurso(indisponivel,"AGUA",false); recurso(outro,"AGUA",true); recurso(inativo,"AGUA",true);
        for (String[] consulta : new String[][]{{"","3"},{"?bairro=cEnTrO","2"},{"?tipoRecurso=AGUA","2"},
                {"?status=INATIVO","1"},{"?bairro=CENTRO&tipoRecurso=AGUA","1"},
                {"?bairro=centro&status=INATIVO&tipoRecurso=AGUA","1"},{"?bairro=ausente","0"}}) {
            var response=request("GET","/api/pontos/busca"+consulta[0],null,null);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(response.body()).path("page").path("totalElements").asInt()).isEqualTo(Integer.parseInt(consulta[1]));
        }
        var pagina=mapper.readTree(request("GET","/api/pontos/busca?tipoRecurso=AGUA&size=1&sort=id,desc",null,null).body());
        String next=pagina.path("_links").path("next").path("href").asText();
        assertThat(next).contains("tipoRecurso=AGUA","size=1","sort=id,desc","page=1");
        var proxima=request("GET",URI.create(next).getRawPath()+"?"+URI.create(next).getRawQuery(),null,null);
        assertThat(mapper.readTree(proxima.body()).path("_embedded").path("pontos").get(0).path("id").asLong()).isEqualTo(ativo);
    }

    @Test
    void senhaExatamente72BytesAceitaESufixoRejeitadoInclusiveMultibyte() throws Exception {
        for (String senha : new String[]{"a".repeat(72),"á".repeat(36)}) {
            String email="limite"+senha.length()+"@example.test";
            String cadastro=mapper.writeValueAsString(java.util.Map.of("nome","Limite","email",email,"senha",senha));
            assertThat(request("POST","/api/auth/cadastro",cadastro,null).statusCode()).isEqualTo(201);
            assertThat(request("POST","/api/auth/login",mapper.writeValueAsString(java.util.Map.of("email",email,"senha",senha)),null).statusCode()).isEqualTo(200);
            assertThat(request("POST","/api/auth/login",mapper.writeValueAsString(java.util.Map.of("email",email,"senha",senha+"x")),null).statusCode()).isEqualTo(400);
            assertThat(request("POST","/api/auth/cadastro",mapper.writeValueAsString(java.util.Map.of("nome","Limite","email","extra"+email,"senha",senha+"x")),null).statusCode()).isEqualTo(400);
        }
    }

    @Test
    void normalizaEmailERejeitaEntradasInvalidasSemHash() throws Exception {
        var cadastro=request("POST","/api/auth/cadastro","{\"nome\":\"Pessoa\",\"email\":\"  Pessoa@Example.Test  \",\"senha\":\"SenhaTeste2026!\"}",null);
        assertThat(cadastro.statusCode()).isEqualTo(201);
        assertThat(mapper.readTree(cadastro.body()).path("email").asText()).isEqualTo("pessoa@example.test");
        assertThat(request("POST","/api/auth/cadastro","{\"nome\":\"Pessoa\",\"email\":\"pessoa@example.test\",\"senha\":\"SenhaTeste2026!\"}",null).statusCode()).isEqualTo(409);
        for(String body: new String[]{"{}","{\"nome\":\"Pessoa\",\"email\":\"invalido\",\"senha\":\"SenhaTeste2026!\"}","{\"nome\":\"Pessoa\",\"email\":\"ok@example.test\",\"senha\":\"curta\"}"}) {
            var response=request("POST","/api/auth/cadastro",body,null);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).doesNotContain("senhaHash","$2a$","ConstraintViolationException");
        }
    }

    @Test
    void tokensAusenteInvalidoExpiradoPapelErradoEUsuarioInativoNaoAutorizam() throws Exception {
        String cidadao=cadastrarLogin("negativos@example.test",false);
        String gestor=cadastrarLogin("admin@example.test",true);
        String corpo="{}";
        for(String path:new String[]{"/api/pontos","/api/recursos","/api/medicoes"}) {
            assertThat(request("POST",path,corpo,null).statusCode()).isEqualTo(401);
            assertThat(request("POST",path,corpo,"invalido").statusCode()).isEqualTo(401);
            assertThat(request("POST",path,corpo,cidadao).statusCode()).isEqualTo(403);
            assertThat(request("POST",path,corpo,gestor).statusCode()).isEqualTo(400);
        }
        assertThat(request("GET","/actuator/metrics",null,cidadao).statusCode()).isEqualTo(403);
        assertThat(request("GET","/actuator/metrics",null,gestor).statusCode()).isEqualTo(200);
        Long id=jdbc.queryForObject("select id from usuario where email='admin@example.test'",Long.class);
        String expirado=io.jsonwebtoken.Jwts.builder().subject(id.toString()).expiration(java.util.Date.from(java.time.Instant.now().minusSeconds(5)))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode("dGVzdC1vbmx5LXNlY3JldC0zMi1ieXRlcy1taW5pbXVtLTAwMDA="))).compact();
        var response=request("POST","/api/pontos",corpo,expirado);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(mapper.readTree(response.body()).path("path").asText()).isEqualTo("/api/pontos");
        assertThat(response.body()).doesNotContain("ExpiredJwtException","signature","expiration");
        jdbc.update("update usuario set ativo=false where id=?",id);
        assertThat(request("GET","/api/ocorrencias/minhas",null,gestor).statusCode()).isEqualTo(401);
        assertThat(request("POST","/api/auth/login","{\"email\":\"admin@example.test\",\"senha\":\"SenhaTeste2026!\"}",null).statusCode()).isEqualTo(401);
        assertThat(request("POST","/api/auth/login","{\"email\":\"negativos@example.test\",\"senha\":\"Errada2026!\"}",null).statusCode()).isEqualTo(401);
    }

    @Test
    void corsPreflightPermiteSoOrigemConfiguradaSemCredenciais() throws Exception {
        String base="http://127.0.0.1:"+environment.getRequiredProperty("local.server.port");
        try(var client=HttpClient.newHttpClient()) {
            for(String origin:new String[]{"http://localhost:3000","https://nao-permitida.test"}) {
                var response=client.send(HttpRequest.newBuilder(URI.create(base+"/api/pontos"))
                        .header("Origin",origin).header("Access-Control-Request-Method","POST")
                        .header("Access-Control-Request-Headers","authorization,content-type")
                        .method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
                if(origin.contains("localhost")) {
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(origin);
                    assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
                } else {
                    assertThat(response.statusCode()).isEqualTo(403);
                    assertThat(mapper.readTree(response.body()).path("status").asInt()).isEqualTo(403);
                }
            }
        }
    }

    @Test
    void chaveJwtVaziaFracaOuBase64InvalidaFalhaSemExporValor() {
        for(String secret:new String[]{"","fraca","%%%"}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(()->new br.com.fiap.refugio.service.JwtService(secret,3600))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        }
    }

    @Test
    void cacheAcertaEInvalidaPontoRecursoMovidoEMedicaoAposCommit() throws Exception {
        String gestor=cadastrarLogin("cache-mutacoes@example.test",true);
        long primeiro=ponto("REF-910","Centro","ATIVO"), segundo=ponto("REF-911","Outro","ATIVO");
        var stats=emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        String path="/api/pontos/"+primeiro;
        assertThat(request("GET",path,null,null).statusCode()).isEqualTo(200);
        stats.clear();
        assertThat(request("GET",path,null,null).statusCode()).isEqualTo(200);
        assertThat(stats.getPrepareStatementCount()).isZero();
        var novo=request("POST","/api/recursos","{\"pontoId\":"+primeiro+",\"tipo\":\"AGUA\",\"disponivel\":true}",gestor);
        assertThat(novo.statusCode()).isEqualTo(201);
        long recurso=mapper.readTree(novo.body()).path("id").asLong();
        assertThat(mapper.readTree(request("GET",path,null,null).body()).path("recursos")).hasSize(1);
        request("GET","/api/pontos/"+segundo,null,null);
        assertThat(request("PUT","/api/recursos/"+recurso,"{\"pontoId\":"+segundo+",\"tipo\":\"AGUA\",\"disponivel\":false}",gestor).statusCode()).isEqualTo(204);
        assertThat(mapper.readTree(request("GET",path,null,null).body()).path("recursos")).isEmpty();
        assertThat(mapper.readTree(request("GET","/api/pontos/"+segundo,null,null).body()).path("recursos")).hasSize(1);
        assertThat(request("DELETE","/api/recursos/"+recurso,null,gestor).statusCode()).isEqualTo(204);
        assertThat(mapper.readTree(request("GET","/api/pontos/"+segundo,null,null).body()).path("recursos")).isEmpty();
        assertThat(request("POST","/api/medicoes","{\"pontoId\":"+primeiro+",\"temperaturaC\":35,\"umidadePct\":50,\"origem\":\"ARDUINO\",\"medidaEm\":\"2026-11-09T10:00:00Z\"}",gestor).statusCode()).isEqualTo(201);
        assertThat(mapper.readTree(request("GET",path,null,null).body()).path("etiqueta").asText()).isEqualTo("ATENCAO_DIDATICA");
        assertThat(request("PUT",path,"{\"nome\":\"Nome atualizado\",\"logradouro\":\"Rua\",\"numero\":\"1\",\"bairro\":\"Centro\",\"cidade\":\"Cidade\",\"uf\":\"SP\",\"horarioTexto\":\"08h\",\"status\":\"ATIVO\"}",gestor).statusCode()).isEqualTo(204);
        assertThat(mapper.readTree(request("GET",path,null,null).body()).path("nome").asText()).isEqualTo("Nome atualizado");
    }

    @Test
    void openapiTodosErrosReferenciamApiErrorESucessosNaoTemCorpo204() throws Exception {
        var json=mapper.readTree(request("GET","/v3/api-docs",null,null).body());
        json.path("paths").properties().forEach(path->path.getValue().properties().forEach(operation->{
            assertThat(operation.getValue().path("summary").asText()).isNotBlank();
            operation.getValue().path("responses").properties().forEach(response->{
                if(Integer.parseInt(response.getKey())>=400) {
                    response.getValue().path("content").properties().forEach(media->assertThat(media.getValue().path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/ApiError"));
                }
                if(response.getKey().equals("204")) assertThat(response.getValue().has("content")).isFalse();
            });
        }));
        assertThat(json.path("components").path("schemas").has("CadastroRequest")).isTrue();
        assertThat(json.path("components").path("schemas").has("OcorrenciaCreate")).isTrue();
        assertThat(request("GET","/swagger-ui/index.html",null,null).statusCode()).isEqualTo(200);
    }

    @Test
    void ocorrenciasValidamTodasCategoriasEDescricaoSemAceitarIdentidadeDoCliente() throws Exception {
        String cidadao=cadastrarLogin("categorias@example.test",false);
        String gestor=cadastrarLogin("categorias-gestor@example.test",true);
        long ponto=ponto("REF-920","Centro","ATIVO");
        Long usuario=jdbc.queryForObject("select id from usuario where email='categorias@example.test'",Long.class);
        for(String[] categoria:new String[][]{{"SEM_AGUA","MEDIA"},{"SEM_SOMBRA","MEDIA"},{"OUTRO","BAIXA"},{"ACESSO_BLOQUEADO","ALTA"}}) {
            var criada=request("POST","/api/ocorrencias",mapper.writeValueAsString(java.util.Map.of("pontoId",ponto,"categoria",categoria[0],"descricao","a".repeat(10),"usuarioId",99999)),cidadao);
            assertThat(criada.statusCode()).isEqualTo(201);
            var json=mapper.readTree(criada.body());
            assertThat(json.path("prioridade").asText()).isEqualTo(categoria[1]);
            assertThat(json.path("usuarioId").asLong()).isEqualTo(usuario);
            assertThat(json.path("resolvidaEm").isNull()).isTrue();
            assertThat(request("PATCH","/api/ocorrencias/"+json.path("id").asLong()+"/status","{\"status\":\"ABERTA\"}",gestor).statusCode()).isEqualTo(409);
        }
        for(int tamanho:new int[]{9,501}) assertThat(request("POST","/api/ocorrencias",mapper.writeValueAsString(java.util.Map.of("pontoId",ponto,"categoria","OUTRO","descricao","a".repeat(tamanho))),cidadao).statusCode()).isEqualTo(400);
        assertThat(request("POST","/api/ocorrencias",mapper.writeValueAsString(java.util.Map.of("pontoId",ponto,"categoria","OUTRO","descricao","a".repeat(500))),gestor).statusCode()).isEqualTo(201);
        assertThat(request("POST","/api/ocorrencias","{\"pontoId\":99999,\"categoria\":\"OUTRO\",\"descricao\":\"Relato de teste\"}",cidadao).statusCode()).isEqualTo(404);
        assertThat(request("POST","/api/ocorrencias","{\"pontoId\":1,\"categoria\":\"INVALIDO\",\"descricao\":\"Relato de teste\"}",cidadao).statusCode()).isEqualTo(400);
        assertThat(request("PATCH","/api/ocorrencias/99999/status","{\"status\":\"EM_ANALISE\"}",gestor).statusCode()).isEqualTo(404);
        var minhas=mapper.readTree(request("GET","/api/ocorrencias/minhas?size=1",null,cidadao).body());
        assertThat(minhas.path("page").path("totalElements").asInt()).isEqualTo(4);
        assertThat(minhas.path("_links").has("next")).isTrue();
        assertThat(mapper.readTree(request("GET","/api/ocorrencias/minhas",null,gestor).body()).path("page").path("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void limitesPaginacaoDeBuscaEMinhasPreservamContratoJpa() throws Exception {
        String cidadao=cadastrarLogin("pagina@example.test",false);
        long ponto = ponto("REF-960", "Centro", "ATIVO");
        for(String path:new String[]{"/api/pontos", "/api/pontos/busca", "/api/ocorrencias/minhas", "/api/pontos/" + ponto + "/medicoes"}) {
            for(String query:new String[]{"page=-1","size=101","size=0","page=2147483647&size=100","sort=invalido,asc"}) {
                var response=request("GET",path+"?"+query,null,cidadao);
                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(mapper.readTree(response.body()).path("status").asInt()).isEqualTo(400);
            }
            for (String query : new String[]{"page=2147483647&size=1", "page=21474836&size=100"}) {
                var limite=request("GET",path+"?"+query,null,cidadao);
                assertThat(limite.statusCode()).isEqualTo(200);
                var json = mapper.readTree(limite.body());
                assertThat(json.has("_embedded")).isFalse();
                assertThat(json.path("_links").has("next")).as(path + "?" + query).isFalse();
            }
        }
    }

    @Test
    void assinaturaIncorretaBasicESessaoNaoSubstituemJwt() throws Exception {
        String cidadao=cadastrarLogin("assinatura@example.test",false);
        String adulterado=cidadao.substring(0,cidadao.lastIndexOf('.')+1)+"AAAA";
        assertThat(request("GET","/api/ocorrencias/minhas",null,adulterado).statusCode()).isEqualTo(401);
        String base="http://127.0.0.1:"+environment.getRequiredProperty("local.server.port");
        try(var client=HttpClient.newHttpClient()) {
            var basic=client.send(HttpRequest.newBuilder(URI.create(base+"/api/ocorrencias/minhas"))
                    .header("Authorization","Basic YTpi").GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(basic.statusCode()).isEqualTo(401);
            var auth=request("GET","/api/ocorrencias/minhas",null,cidadao);
            assertThat(auth.headers().firstValue("Set-Cookie")).isEmpty();
            assertThat(request("GET","/api/ocorrencias/minhas",null,null).statusCode()).isEqualTo(401);
        }
    }

    @Test
    void conflitoRecursoNaoInvalidaDetalheEExclusaoPontoRetiraCache() throws Exception {
        String gestor=cadastrarLogin("rollback@example.test",true);
        long primeiro=ponto("REF-930","Centro","ATIVO"),segundo=ponto("REF-931","Centro","ATIVO");
        recurso(primeiro,"AGUA",true); recurso(segundo,"AGUA",false);
        Long id=jdbc.queryForObject("select id from recurso_ponto where ponto_id=?",Long.class,primeiro);
        request("GET","/api/pontos/"+primeiro,null,null);
        request("GET","/api/pontos/"+segundo,null,null);
        assertThat(request("PUT","/api/recursos/"+id,"{\"pontoId\":"+segundo+",\"tipo\":\"AGUA\",\"disponivel\":true}",gestor).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select ponto_id from recurso_ponto where id=?",Long.class,id)).isEqualTo(primeiro);
        assertThat(mapper.readTree(request("GET","/api/pontos/"+primeiro,null,null).body()).path("recursos").get(0).path("disponivel").asBoolean()).isTrue();
        long vazio=ponto("REF-932","Centro","ATIVO");
        request("GET","/api/pontos/"+vazio,null,null);
        assertThat(request("DELETE","/api/pontos/"+vazio,null,gestor).statusCode()).isEqualTo(204);
        assertThat(cache.getCache("pontoDetalhe").get(vazio)).isNull();
        assertThat(request("GET","/api/pontos/"+vazio,null,null).statusCode()).isEqualTo(404);
    }

}
