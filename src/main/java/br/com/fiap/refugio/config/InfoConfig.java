package br.com.fiap.refugio.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.*;

import java.util.Map;

@Configuration
public class InfoConfig {
    @Bean
    InfoContributor informacoes(@Value("${app.aviso-demonstracao}") String aviso) {
        return builder ->
                builder.withDetail(
                        "app",
                        Map.of(
                                "nome",
                                "Refúgio",
                                "descricao",
                                "API demonstrativa de pontos de alívio térmico",
                                "avisoDados",
                                aviso));
    }
}
