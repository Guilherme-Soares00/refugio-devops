package br.com.fiap.refugio;

import org.springframework.test.context.DynamicPropertyRegistry;

final class BancoTeste {
    private BancoTeste() {
    }

    static String url() {
        String url = System.getenv().getOrDefault("TEST_DATABASE_URL", "jdbc:postgresql://localhost:55432/refugio_test");
        if (!url.matches("jdbc:postgresql://[^/]+/refugio_test")) {
            throw new IllegalArgumentException("Os testes destrutivos exigem o banco separado refugio_test, sem parâmetros na URL");
        }
        return url;
    }

    static String usuario() {
        return System.getenv().getOrDefault("TEST_DATABASE_USERNAME", "refugio_test");
    }

    static String senha() {
        return System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "refugio_test_only");
    }

    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> "dGVzdC1vbmx5LXNlY3JldC0zMi1ieXRlcy1taW5pbXVtLTAwMDA=");
        registry.add("spring.datasource.url", BancoTeste::url);
        registry.add("spring.datasource.username", BancoTeste::usuario);
        registry.add("spring.datasource.password", BancoTeste::senha);
    }
}
