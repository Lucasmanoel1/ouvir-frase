package com.lucasmanoel.ouvirfrase.controller;

import com.lucasmanoel.ouvirfrase.dto.RespostaBusca;
import com.lucasmanoel.ouvirfrase.dto.ResultadoBusca;
import com.lucasmanoel.ouvirfrase.service.BuscaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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
        when(buscaService.buscar("hello world", "en")).thenReturn(new RespostaBusca(List.of(
                new ResultadoBusca("abc123XYZ_-", "Titulo", 9.0, 15.0, "well hello world", 1.0, true, true))));

        mockMvc.perform(get("/api/busca").param("frase", "hello world"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[0].youtubeId").value("abc123XYZ_-"))
                .andExpect(jsonPath("$.resultados[0].titulo").value("Titulo"))
                .andExpect(jsonPath("$.resultados[0].inicioSeg").value(9.0))
                .andExpect(jsonPath("$.resultados[0].fimSeg").value(15.0))
                .andExpect(jsonPath("$.resultados[0].legenda").value("well hello world"))
                .andExpect(jsonPath("$.resultados[0].pontuacao").value(1.0))
                .andExpect(jsonPath("$.resultados[0].exata").value(true))
                .andExpect(jsonPath("$.resultados[0].legendaAutomatica").value(true));
    }

    @Test
    void deveRejeitarFraseEmBrancoComErroEmJson() throws Exception {
        mockMvc.perform(get("/api/busca").param("frase", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("requisicao_invalida"))
                .andExpect(jsonPath("$.mensagem").isNotEmpty());
    }
}
