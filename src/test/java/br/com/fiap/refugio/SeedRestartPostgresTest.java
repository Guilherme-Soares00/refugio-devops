package br.com.fiap.refugio;

import br.com.fiap.refugio.config.SeedDemonstrativo;


import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SeedRestartPostgresTest {
    @Test
    void seedOptInReiniciaSemDuplicarComIdsLocaisEDadosPreexistentes() {
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(BancoTeste.url(), BancoTeste.usuario(), BancoTeste.senha()));
        admin.execute("create schema if not exists etapa2_restart");
        try {
            try (var contexto = iniciar(false, "create")) {
                assertThat(contexto.getBeanProvider(SeedDemonstrativo.class).getIfAvailable()).isNull();
                JdbcTemplate jdbc = contexto.getBean(JdbcTemplate.class);
                assertThat(jdbc.queryForObject("select count(*) from ponto_refugio", Integer.class)).isZero();
                jdbc.update("insert into usuario(id,nome,email,senha_hash,papel,ativo,criado_em) values (100,'Preexistente','existente@example.test','hash-preservado','CIDADAO',true,'2026-01-01T00:00:00Z')");
                jdbc.update("insert into ponto_refugio(id,codigo_publico,nome,logradouro,numero,bairro,cidade,uf,horario_texto,status,cadastrado_em) values (100,'REF-999','Preexistente','Rua prévia','1','Bairro prévio','Cidade prévia','SP','09:00-10:00','ATIVO','2026-01-01T00:00:00Z')");
                jdbc.execute("select setval(pg_get_serial_sequence('usuario','id'),100)");
                jdbc.execute("select setval(pg_get_serial_sequence('ponto_refugio','id'),100)");
            }
            List<List<Map<String, Object>>> antes;
            try (var contexto = iniciar(true, "update")) {
                JdbcTemplate jdbc = contexto.getBean(JdbcTemplate.class);
                assertThat(jdbc.queryForObject("select count(*) from usuario", Integer.class)).isEqualTo(11);
                assertThat(jdbc.queryForObject("select count(*) from ponto_refugio", Integer.class)).isEqualTo(11);
                assertThat(jdbc.queryForObject("select min(ponto_id) from ocorrencia", Long.class)).isGreaterThan(100);
                assertThat(jdbc.queryForObject("select min(usuario_id) from ocorrencia", Long.class)).isGreaterThan(100);
                jdbc.update("update ocorrencia set status='RESOLVIDA', resolvida_em='2026-11-10T15:00:00Z' where aberta_em='2026-11-09T11:01:00Z'");
                jdbc.update("update ponto_refugio set nome='Ajustado' where codigo_publico='REF-001'");
                antes = snapshot(jdbc);
            }
            try (var contexto = iniciar(true, "validate")) {
                assertThat(snapshot(contexto.getBean(JdbcTemplate.class))).isEqualTo(antes);
            }
        } finally {
            admin.execute("drop schema etapa2_restart cascade");
        }
    }

    private ConfigurableApplicationContext iniciar(boolean seed, String ddl) {
        return new SpringApplicationBuilder(RefugioApplication.class).web(WebApplicationType.NONE).run(
                "--app.jwt.secret=dGVzdC1vbmx5LXNlY3JldC0zMi1ieXRlcy1taW5pbXVtLTAwMDA=",
                "--spring.datasource.url=" + BancoTeste.url(),
                "--spring.datasource.username=" + BancoTeste.usuario(),
                "--spring.datasource.password=" + BancoTeste.senha(),
                "--spring.datasource.hikari.schema=etapa2_restart",
                "--spring.jpa.properties.hibernate.default_schema=etapa2_restart",
                "--spring.jpa.hibernate.ddl-auto=" + ddl,
                "--app.seed.enabled=" + seed);
    }

    private List<List<Map<String, Object>>> snapshot(JdbcTemplate jdbc) {
        return Stream.of("usuario", "ponto_refugio", "recurso_ponto", "medicao", "ocorrencia")
                .map(tabela -> jdbc.queryForList("select * from " + tabela + " order by id")).toList();
    }
}
