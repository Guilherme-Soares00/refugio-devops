package br.com.fiap.refugio;

import br.com.fiap.refugio.config.ApiExceptionHandler;


import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiExceptionHandlerTest {
    @Test
    void naoExpoeDetalhesPrivadosNoErroInesperado() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FalhaController()).setControllerAdvice(new ApiExceptionHandler()).build();
        String body = mvc.perform(get("/falha")).andExpect(status().isInternalServerError())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value(500)).andExpect(jsonPath("$.erro").value("Internal Server Error"))
                .andExpect(jsonPath("$.mensagem").value("Erro interno do servidor"))
                .andExpect(jsonPath("$.path").value("/falha")).andExpect(jsonPath("$.timestamp").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("segredo", "SQL", "RuntimeException", "stackTrace");
    }

    @RestController
    class FalhaController {
        @GetMapping("/falha")
        public String falhar() {
            throw new IllegalStateException("SQL privado e segredo não podem aparecer na resposta");
        }
    }
}
