package com.lucasmanoel.ouvirfrase.controller;

import com.lucasmanoel.ouvirfrase.dto.LegendaTrecho;
import com.lucasmanoel.ouvirfrase.service.LegendaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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
    void deveDevolverALegendaEmOrdemComTraducao() throws Exception {
        when(legendaService.legendaDoVideo("abc123XYZ_-")).thenReturn(List.of(
                new LegendaTrecho("hello there", "olá", 1.5, 2.0),
                new LegendaTrecho("how are you", null, 3.5, 2.5)));

        mockMvc.perform(get("/api/videos/abc123XYZ_-/legenda"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=86400")))
                .andExpect(jsonPath("$[0].texto").value("hello there"))
                .andExpect(jsonPath("$[0].traducao").value("olá"))
                .andExpect(jsonPath("$[0].inicioSeg").value(1.5))
                .andExpect(jsonPath("$[0].duracaoSeg").value(2.0))
                .andExpect(jsonPath("$[1].traducao").doesNotExist());
    }

    @Test
    void deveRejeitarIdInvalidoComErroEmJson() throws Exception {
        mockMvc.perform(get("/api/videos/a b/legenda"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("requisicao_invalida"));
    }
}
