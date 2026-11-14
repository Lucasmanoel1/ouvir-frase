package com.lucasmanoel.idiomando.service;

import com.lucasmanoel.idiomando.dto.RespostaBusca;
import com.lucasmanoel.idiomando.dto.ResultadoBusca;
import com.lucasmanoel.idiomando.ingestao.indexacao.IndexadorDeVideo;
import com.lucasmanoel.idiomando.ingestao.legenda.TrechoLegenda;
import com.lucasmanoel.idiomando.repository.OcorrenciaRepository;
import com.lucasmanoel.idiomando.repository.TrechoRepository;
import com.lucasmanoel.idiomando.repository.VideoRepository;
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
        RespostaBusca resposta = buscaService.buscar("I really adore this idea", "en");

        assertThat(resposta.resultados()).hasSize(1);
        ResultadoBusca r = resposta.resultados().get(0);
        assertThat(r.exata()).isFalse();
        assertThat(r.pontuacao()).isEqualTo(0.8, within(1e-9));
        assertThat(r.inicioSeg()).isEqualTo(10.0, within(0.001));
        assertThat(r.ultimaPalavraSeg()).isEqualTo(14.333, within(0.001)); // idea: 3ª de 3 em 13-15 s
        assertThat(r.falaInicioSeg()).isEqualTo(10.0, within(0.001));
        assertThat(r.falaFimSeg()).isEqualTo(15.0, within(0.001));
        assertThat(r.legenda()).contains("I really love this wonderful idea");
    }

    @Test
    void deveCortarOFimDaFalaNoInicioDaSeguinte() {
        // legenda automática: a duração do trecho (20 a 25 s) invade o seguinte, que começa aos 23 s;
        // "soon" não existe: 3 de 4 palavras (75%)
        indexador.indexar("auto01", "Video auto", "en", List.of(
                new TrechoLegenda("we should go home", 20.0, 5.0),
                new TrechoLegenda("right now", 23.0, 4.0)));

        ResultadoBusca r = buscaService.buscar("we should go soon", "en").resultados().get(0);

        assertThat(r.exata()).isFalse();
        assertThat(r.falaInicioSeg()).isEqualTo(20.0, within(0.001));
        assertThat(r.falaFimSeg()).isEqualTo(23.0, within(0.001));
    }
}
