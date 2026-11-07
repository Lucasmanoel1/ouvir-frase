package com.lucasmanoel.ouvirfrase.controller;

import com.lucasmanoel.ouvirfrase.dto.LegendaTrecho;
import com.lucasmanoel.ouvirfrase.service.LegendaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LegendaController.class)
class LegendaControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean LegendaService legendaService;

    @Test
    void deveDevolverALegendaEmOrdem() throws Exception {
        when(legendaService.legendaDoVideo("abc123XYZ_-")).thenReturn(List.of(
                new LegendaTrecho("hello there", 1.5, 2.0),
                new LegendaTrecho("how are you", 3.5, 2.5)));

        mockMvc.perform(get("/api/videos/abc123XYZ_-/legenda"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=86400")))
                .andExpect(jsonPath("$[0].texto").value("hello there"))
                .andExpect(jsonPath("$[0].inicioSeg").value(1.5))
                .andExpect(jsonPath("$[0].duracaoSeg").value(2.0))
                .andExpect(jsonPath("$[1].texto").value("how are you"));
    }

    @Test
    void deveRejeitarIdInvalidoComErroEmJson() throws Exception {
        mockMvc.perform(get("/api/videos/a b/legenda"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("requisicao_invalida"));
    }

    @Test
    void deveDevolverListaVaziaSemCacheParaVideoDesconhecido() throws Exception {
        when(legendaService.legendaDoVideo("naoexiste1")).thenReturn(List.of());

        mockMvc.perform(get("/api/videos/naoexiste1/legenda"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deveRejeitarIdComMaisDe20Caracteres() throws Exception {
        mockMvc.perform(get("/api/videos/" + "a".repeat(21) + "/legenda"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("requisicao_invalida"));
    }

    @Test
    void deveRejeitarIdComBarraCodificada() throws Exception {
        // 400 ou 404, conforme o Spring resolver o caminho; nunca 200 nem 500
        mockMvc.perform(get(URI.create("/api/videos/abc%2Fdef/legenda")))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(400, 404));
    }
}
