package br.com.fiap.refugio.config;

import br.com.fiap.refugio.dto.ApiError;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.*;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

import java.util.List;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI openAPI(@Value("${app.aviso-demonstracao}") String aviso) {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Refúgio API")
                                .version("1.0")
                                .description(
                                        "Pontos de alívio térmico, recursos, medições e ocorrências. "
                                                + aviso))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")));
    }

    @Bean
    OpenApiCustomizer contratos() {
        return api -> {
            ModelConverters.getInstance()
                    .read(ApiError.class)
                    .forEach(api.getComponents()::addSchemas);
            api.getPaths()
                    .forEach(
                            (path, item) ->
                                    item.readOperationsMap()
                                            .forEach(
                                                    (method, operation) ->
                                                            documentarOperacao(
                                                                    path, method, operation)));
        };
    }

    private static void documentarOperacao(
            String path, PathItem.HttpMethod method, Operation operation) {
        boolean auth = path.startsWith("/api/auth/");
        boolean leituraPublica =
                method == PathItem.HttpMethod.GET && path.startsWith("/api/pontos");
        boolean protegido = !auth && !leituraPublica;
        if (protegido)
            operation.setSecurity(List.of(new SecurityRequirement().addList("bearerAuth")));
        operation.setSummary(resumo(path, method));
        operation.getResponses().entrySet().removeIf(e -> Integer.parseInt(e.getKey()) >= 400);
        if (method == PathItem.HttpMethod.POST) {
            String sucesso = path.endsWith("/login") ? "200" : "201";
            var response = operation.getResponses().remove("200");
            if (response == null) response = new ApiResponse();
            operation
                    .getResponses()
                    .addApiResponse(
                            sucesso,
                            response.description(
                                    sucesso.equals("201") ? "Registro criado" : "Login realizado"));
        } else if (method != PathItem.HttpMethod.GET) {
            operation.getResponses().clear();
            operation
                    .getResponses()
                    .addApiResponse(
                            "204",
                            new ApiResponse().description("Operação concluída sem conteúdo"));
        }
        erro(operation, "400", "Dados da requisição inválidos");
        erro(operation, "401", "Autenticação necessária ou token inválido");
        if (protegido) erro(operation, "403", "Papel sem permissão");
        if (!auth && !path.endsWith("/minhas")) erro(operation, "404", "Registro não encontrado");
        if (method != PathItem.HttpMethod.GET && !path.endsWith("/login"))
            erro(operation, "409", "Conflito com dados, vínculos ou transição de status");
    }

    private static void erro(Operation operation, String status, String descricao) {
        var schema = new Schema<>().$ref("#/components/schemas/ApiError");
        var conteudo =
                new Content().addMediaType("application/json", new MediaType().schema(schema));
        var resposta = new ApiResponse().description(descricao).content(conteudo);
        operation.getResponses().addApiResponse(status, resposta);
    }

    private static String resumo(String path, PathItem.HttpMethod method) {
        if (path.endsWith("/cadastro")) return "Cadastrar cidadão";
        if (path.endsWith("/login")) return "Autenticar usuário e emitir JWT";
        if (path.endsWith("/busca")) return "Buscar pontos por bairro, status e recurso disponível";
        if (path.endsWith("/minhas")) return "Listar ocorrências do usuário autenticado";
        if (path.endsWith("/status")) return "Avançar status da ocorrência";
        String dominio;
        if (path.contains("recursos")) {
            dominio = "recursos";
        } else if (path.contains("medicoes")) {
            dominio = "medições";
        } else if (path.contains("ocorrencias")) {
            dominio = "ocorrências";
        } else {
            dominio = "pontos de refúgio";
        }
        return switch (method) {
            case GET -> "Consultar " + dominio;
            case POST -> "Cadastrar " + dominio;
            case PUT -> "Atualizar " + dominio;
            case DELETE -> "Excluir " + dominio;
            default -> "Gerenciar " + dominio;
        };
    }
}
