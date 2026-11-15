package br.com.fiap.refugio;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:55432/refugio_test}",
        "spring.datasource.username=${TEST_DATABASE_USERNAME:refugio_test}",
        "spring.datasource.password=${TEST_DATABASE_PASSWORD:refugio_test_only}",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed.enabled=true"
})
abstract class SeedPostgresTest {
    protected final JdbcTemplate jdbc;

    SeedPostgresTest(JdbcTemplate jdbc) {
        BancoTeste.url();
        this.jdbc = jdbc;
    }

    @org.springframework.test.context.DynamicPropertySource
    static void bancoSeparado(org.springframework.test.context.DynamicPropertyRegistry registry) {
        BancoTeste.configurar(registry);
    }

    @Test
    void carregaAsCincoTabelasComQuantidadesAprovadasNoPostgres() {
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = 'public' and table_name in ('usuario', 'ponto_refugio', 'recurso_ponto', 'medicao', 'ocorrencia')", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from usuario", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from ponto_refugio", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from recurso_ponto", Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("select count(*) from medicao", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from ocorrencia", Integer.class)).isEqualTo(10);
    }
}
