package br.com.fiap.refugio;

import br.com.fiap.refugio.model.CategoriaOcorrencia;
import br.com.fiap.refugio.model.Medicao;
import br.com.fiap.refugio.model.Ocorrencia;
import br.com.fiap.refugio.model.OrigemMedicao;
import br.com.fiap.refugio.model.Papel;
import br.com.fiap.refugio.model.PontoRefugio;
import br.com.fiap.refugio.model.PrioridadeOcorrencia;
import br.com.fiap.refugio.model.StatusOcorrencia;
import br.com.fiap.refugio.model.StatusPonto;
import br.com.fiap.refugio.model.Usuario;


import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ValidacaoDominioTest {
    private static final jakarta.validation.ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();
    private static final Instant INSTANTE = Instant.parse("2026-11-09T09:00:00Z");

    @AfterAll
    static void fechar() {
        FACTORY.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.1", "60.1", "20.55"})
    void rejeitaTemperaturaForaDoIntervaloOuComMaisDeUmaCasa(String valor) {
        assertThat(VALIDATOR.validate(new Medicao(ponto("REF-011"), new BigDecimal(valor),
                new BigDecimal("50.0"), OrigemMedicao.ARDUINO, INSTANTE))).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.1", "100.1"})
    void rejeitaUmidadeForaDoIntervalo(String valor) {
        assertThat(VALIDATOR.validate(new Medicao(ponto("REF-011"), new BigDecimal("30.0"),
                new BigDecimal(valor), OrigemMedicao.ARDUINO, INSTANTE))).isNotEmpty();
    }

    @Test
    void aceitaOsLimitesInclusivosDasMedicoes() {
        assertThat(VALIDATOR.validate(new Medicao(ponto("REF-011"), new BigDecimal("0.0"),
                new BigDecimal("0.0"), OrigemMedicao.ARDUINO, INSTANTE))).isEmpty();
        assertThat(VALIDATOR.validate(new Medicao(ponto("REF-011"), new BigDecimal("60.0"),
                new BigDecimal("100.0"), OrigemMedicao.ARDUINO, INSTANTE))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 9, 501})
    void rejeitaDescricaoForaDoTamanho(int tamanho) {
        assertThat(VALIDATOR.validate(ocorrencia("a".repeat(tamanho)))).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 500})
    void aceitaLimitesDaDescricao(int tamanho) {
        assertThat(VALIDATOR.validate(ocorrencia("a".repeat(tamanho)))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"REF-11", "REF-0011", "ref-001", "BAD-001", "REF-00A"})
    void rejeitaCodigoPublicoForaDoFormato(String codigo) {
        assertThat(VALIDATOR.validate(ponto(codigo))).isNotEmpty();
    }

    @Test
    void exigeDadosBasicosEEmailValido() {
        assertThat(VALIDATOR.validate(new Usuario("", "invalido", "", null, true, null))).hasSizeGreaterThanOrEqualTo(5);
        assertThat(VALIDATOR.validate(new Medicao(null, null, null, null, null))).hasSize(5);
        var ponto = ponto("REF-011");
        ponto.setNome(" ");
        ponto.setBairro("");
        ponto.setHorarioTexto("");
        assertThat(VALIDATOR.validate(ponto)).hasSize(3);
    }

    private static PontoRefugio ponto(String codigo) {
        return new PontoRefugio(codigo, "Ponto de teste", "Rua Teste", "1", "Bairro Teste",
                "Cidade Demonstrativa", "SP", "08:00-18:00", StatusPonto.ATIVO, INSTANTE);
    }

    private static Ocorrencia ocorrencia(String descricao) {
        var usuario = new Usuario("Pessoa", "pessoa@example.test", "hash", Papel.CIDADAO, true, INSTANTE);
        return new Ocorrencia(ponto("REF-011"), usuario, CategoriaOcorrencia.OUTRO, descricao,
                PrioridadeOcorrencia.BAIXA, StatusOcorrencia.ABERTA, INSTANTE, null);
    }
}
