package com.lucasmanoel.idiomando.controller;

import com.lucasmanoel.idiomando.dto.RespostaBusca;
import com.lucasmanoel.idiomando.dto.ResultadoBusca;
import com.lucasmanoel.idiomando.service.BuscaService;
import com.lucasmanoel.idiomando.service.FraseLongaDemaisException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BuscaController.class)
class BuscaControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean BuscaService buscaService;

    @Test
    void deveDevolverOContratoDaApi() throws Exception {
        when(buscaService.buscar("hello world", "en")).thenReturn(new RespostaBusca("en", List.of(
                new ResultadoBusca("abc123XYZ_-", "Titulo", 12.0, 14.0, 10.0, 15.0, "well hello world", 1.0, true, true,
                        "legenda latina")), "frase latina"));

        mockMvc.perform(get("/api/busca").param("frase", "hello world"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idioma").value("en"))
                .andExpect(jsonPath("$.resultados[0].youtubeId").value("abc123XYZ_-"))
                .andExpect(jsonPath("$.resultados[0].titulo").value("Titulo"))
                .andExpect(jsonPath("$.resultados[0].inicioSeg").value(12.0))
                .andExpect(jsonPath("$.resultados[0].ultimaPalavraSeg").value(14.0))
                .andExpect(jsonPath("$.resultados[0].falaInicioSeg").value(10.0))
                .andExpect(jsonPath("$.resultados[0].falaFimSeg").value(15.0))
                .andExpect(jsonPath("$.resultados[0].fimSeg").doesNotExist())
                .andExpect(jsonPath("$.resultados[0].legenda").value("well hello world"))
                .andExpect(jsonPath("$.resultados[0].pontuacao").value(1.0))
                .andExpect(jsonPath("$.resultados[0].exata").value(true))
                .andExpect(jsonPath("$.resultados[0].legendaAutomatica").value(true))
                .andExpect(jsonPath("$.resultados[0].legendaTransliterada").value("legenda latina"))
                .andExpect(jsonPath("$.fraseTransliterada").value("frase latina"));
    }

    @Test
    void deveRejeitarFraseEmBrancoComErroEmJson() throws Exception {
        mockMvc.perform(get("/api/busca").param("frase", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("requisicao_invalida"))
                .andExpect(jsonPath("$.mensagem").isNotEmpty());
    }

    @Test
    void deveRejeitarFraseAusente() throws Exception {
        mockMvc.perform(get("/api/busca"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("requisicao_invalida"));
    }

    @Test
    void deveRejeitarFraseCom201Caracteres() throws Exception {
        when(buscaService.buscar("a".repeat(200), "en")).thenReturn(new RespostaBusca("en", List.of(), null));

        mockMvc.perform(get("/api/busca").param("frase", "a".repeat(201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("requisicao_invalida"));
        mockMvc.perform(get("/api/busca").param("frase", "a".repeat(200)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"e1", "english", "en_US", "en-aaaa-bbbb"})
    void deveRejeitarIdiomaForaDoPadraoOuComMaisDe10Caracteres(String idioma) throws Exception {
        mockMvc.perform(get("/api/busca").param("frase", "hello").param("idioma", idioma))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("requisicao_invalida"));
    }

    static Stream<RuntimeException> excecoesDoBanco() {
        return Stream.of(new QueryTimeoutException("statement timeout"),
                new CannotCreateTransactionException("pool esgotado"));
    }

    @ParameterizedTest
    @MethodSource("excecoesDoBanco")
    void deveResponder503EmJsonQuandoOBancoNaoResponde(RuntimeException excecao) throws Exception {
        when(buscaService.buscar(any(), any())).thenThrow(excecao);

        mockMvc.perform(get("/api/busca").param("frase", "hello"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.erro").value("indisponivel"))
                .andExpect(jsonPath("$.mensagem").isNotEmpty());
    }

    @Test
    void deveResponder400QuandoAFraseTemPalavrasDemais() throws Exception {
        when(buscaService.buscar(any(), any())).thenThrow(new FraseLongaDemaisException(15));

        mockMvc.perform(get("/api/busca").param("frase", "hello"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("frase_longa"))
                .andExpect(jsonPath("$.mensagem").value("Use até 15 palavras."));
    }
}
