package br.com.fiap.refugio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class OrganizacaoPacotesTest {
    @Test
    void mantemAsClassesNosPacotesPorCamada() {
        String[] classes = {
                "br.com.fiap.refugio.controller.AuthController",
                "br.com.fiap.refugio.service.AuthService",
                "br.com.fiap.refugio.dto.CadastroRequest",
                "br.com.fiap.refugio.config.JwtFilter",
                "br.com.fiap.refugio.service.JwtService",
                "br.com.fiap.refugio.dto.LoginRequest",
                "br.com.fiap.refugio.model.Papel",
                "br.com.fiap.refugio.config.SecurityErrors",
                "br.com.fiap.refugio.dto.TokenResponse",
                "br.com.fiap.refugio.model.Usuario",
                "br.com.fiap.refugio.repository.UsuarioRepository",
                "br.com.fiap.refugio.dto.UsuarioResponse",
                "br.com.fiap.refugio.dto.ApiError",
                "br.com.fiap.refugio.config.ApiException",
                "br.com.fiap.refugio.config.ApiExceptionHandler",
                "br.com.fiap.refugio.controller.Hipermidia",
                "br.com.fiap.refugio.dto.Pagina",
                "br.com.fiap.refugio.dto.Paginacao",
                "br.com.fiap.refugio.config.CacheConfig",
                "br.com.fiap.refugio.config.InfoConfig",
                "br.com.fiap.refugio.config.OpenApiConfig",
                "br.com.fiap.refugio.config.SecurityConfig",
                "br.com.fiap.refugio.config.SeedDemonstrativo",
                "br.com.fiap.refugio.model.Medicao",
                "br.com.fiap.refugio.controller.MedicaoController",
                "br.com.fiap.refugio.dto.MedicaoCreate",
                "br.com.fiap.refugio.dto.MedicaoDetail",
                "br.com.fiap.refugio.repository.MedicaoRepository",
                "br.com.fiap.refugio.service.MedicaoService",
                "br.com.fiap.refugio.model.OrigemMedicao",
                "br.com.fiap.refugio.model.CategoriaOcorrencia",
                "br.com.fiap.refugio.model.Ocorrencia",
                "br.com.fiap.refugio.controller.OcorrenciaController",
                "br.com.fiap.refugio.dto.OcorrenciaCreate",
                "br.com.fiap.refugio.dto.OcorrenciaDetail",
                "br.com.fiap.refugio.repository.OcorrenciaRepository",
                "br.com.fiap.refugio.service.OcorrenciaService",
                "br.com.fiap.refugio.dto.OcorrenciaStatusUpdate",
                "br.com.fiap.refugio.model.PrioridadeOcorrencia",
                "br.com.fiap.refugio.model.StatusOcorrencia",
                "br.com.fiap.refugio.controller.PontoController",
                "br.com.fiap.refugio.dto.PontoCreate",
                "br.com.fiap.refugio.dto.PontoDetail",
                "br.com.fiap.refugio.model.PontoRefugio",
                "br.com.fiap.refugio.repository.PontoRefugioRepository",
                "br.com.fiap.refugio.service.PontoService",
                "br.com.fiap.refugio.dto.PontoSummary",
                "br.com.fiap.refugio.dto.PontoUpdate",
                "br.com.fiap.refugio.model.StatusPonto",
                "br.com.fiap.refugio.controller.RecursoController",
                "br.com.fiap.refugio.dto.RecursoCreate",
                "br.com.fiap.refugio.dto.RecursoDetail",
                "br.com.fiap.refugio.model.RecursoPonto",
                "br.com.fiap.refugio.repository.RecursoPontoRepository",
                "br.com.fiap.refugio.service.RecursoService",
                "br.com.fiap.refugio.dto.RecursoUpdate",
                "br.com.fiap.refugio.model.TipoRecurso",
                "br.com.fiap.refugio.RefugioApplication"
        };
        for (String nome : classes) {
            assertThatCode(() -> Class.forName(nome)).as(nome).doesNotThrowAnyException();
        }
    }
}
