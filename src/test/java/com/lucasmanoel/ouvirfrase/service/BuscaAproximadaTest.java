package com.lucasmanoel.ouvirfrase.service;

import com.lucasmanoel.ouvirfrase.dto.RespostaBusca;
import com.lucasmanoel.ouvirfrase.dto.ResultadoBusca;
import com.lucasmanoel.ouvirfrase.dto.TrechoLegenda;
import com.lucasmanoel.ouvirfrase.repository.OcorrenciaRepository;
import com.lucasmanoel.ouvirfrase.repository.TrechoRepository;
import com.lucasmanoel.ouvirfrase.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Testcontainers
class BuscaAproximadaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired BuscaService buscaService;
    @Autowired IndexadorDeVideo indexador;
    @Autowired VideoRepository videoRepository;
    @Autowired TrechoRepository trechoRepository;
    @Autowired OcorrenciaRepository ocorrenciaRepository;

    @BeforeEach
    void prepararBanco() {
        ocorrenciaRepository.deleteAllInBatch();
        trechoRepository.deleteAllInBatch();
        videoRepository.deleteAllInBatch();
        indexador.indexar("abc123", "Video de teste", "en", List.of(
                new TrechoLegenda("I really love", 10.0, 3.0),
                new TrechoLegenda("this wonderful idea [Music]", 13.0, 2.0)));
    }

    @Test
    void deveAcharComoAproximadaQuandoUmaPalavraDaFraseNaoExisteNoVideo() {
        // "adore" não existe no vídeo: 4 de 5 palavras (80%), na ordem, cruzando dois trechos
        RespostaBusca resposta = buscaService.buscar("I really adore this idea");

        assertThat(resposta.resultados()).hasSize(1);
        ResultadoBusca r = resposta.resultados().get(0);
        assertThat(r.exata()).isFalse();
        assertThat(r.pontuacao()).isEqualTo(0.8, within(1e-9));
        assertThat(r.inicioSeg()).isEqualTo(7.0, within(0.001));
        assertThat(r.legenda()).contains("I really love this wonderful idea");
    }
}
