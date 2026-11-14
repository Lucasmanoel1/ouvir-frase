package com.lucasmanoel.idiomando.controller;

import com.lucasmanoel.idiomando.ingestao.indexacao.IndexadorDeVideo;
import com.lucasmanoel.idiomando.ingestao.legenda.TrechoLegenda;
import com.lucasmanoel.idiomando.repository.OcorrenciaRepository;
import com.lucasmanoel.idiomando.repository.TrechoRepository;
import com.lucasmanoel.idiomando.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A porta de entrada inteira (validação, normalização, SQL) contra um Postgres de verdade. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApiHostilTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired MockMvc mockMvc;
    @Autowired IndexadorDeVideo indexador;
    @Autowired VideoRepository videoRepository;
    @Autowired TrechoRepository trechoRepository;
    @Autowired OcorrenciaRepository ocorrenciaRepository;

    @BeforeEach
    void prepararBanco() {
        ocorrenciaRepository.deleteAllInBatch();
        trechoRepository.deleteAllInBatch();
        videoRepository.deleteAllInBatch();
        indexador.indexar("eng0001", "Video en", "en", List.of(new TrechoLegenda("I really love this idea", 10.0, 4.0)));
        indexador.indexar("rus0001", "Video ru", "ru", List.of(new TrechoLegenda("привет друзья", 4.0, 2.0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"'", "%", "_", "\\", "'; DROP TABLE video;--", "$1", "?1", "((( ]]]", "\" OR 1=1 --",
            "\u0000", "😀", "日本語", "مرحبا", "%' OR '1'='1"})
    void deveResponder200SemQuebrarNemGravar(String frase) throws Exception {
        long videos = videoRepository.count();
        long trechos = trechoRepository.count();
        long ocorrencias = ocorrenciaRepository.count();

        mockMvc.perform(get("/api/busca").param("frase", frase))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados").isEmpty());

        assertThat(videoRepository.count()).isEqualTo(videos);
        assertThat(trechoRepository.count()).isEqualTo(trechos);
        assertThat(ocorrenciaRepository.count()).isEqualTo(ocorrencias);
    }

    @Test
    void deveAcharAPalavraEnvoltaEmLixo() throws Exception {
        mockMvc.perform(get("/api/busca").param("frase", "love'; --"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[0].youtubeId").value("eng0001"));
    }

    @Test
    void deveAceitar15PalavrasERecusar16() throws Exception {
        mockMvc.perform(get("/api/busca").param("frase", String.join(" ", Collections.nCopies(15, "word"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/busca").param("frase", String.join(" ", Collections.nCopies(16, "word"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("frase_longa"))
                .andExpect(jsonPath("$.mensagem").value("Use até 15 palavras."));
    }

    @Test
    void deveRecusar16PalavrasEmChinesPorCaractere() throws Exception {
        mockMvc.perform(get("/api/busca").param("frase", "好".repeat(16)).param("idioma", "zh-CN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("frase_longa"));
    }
}
