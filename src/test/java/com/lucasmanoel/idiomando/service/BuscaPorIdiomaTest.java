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

@SpringBootTest
@Testcontainers
class BuscaPorIdiomaTest {

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
        // mesmas palavras nos dois idiomas: só o filtro por idioma separa os resultados
        indexador.indexar("eng0001", "Video en", "en", List.of(new TrechoLegenda("la casa grande", 4.0, 2.0)));
        indexador.indexar("spa0001", "Video es", "es", List.of(new TrechoLegenda("la casa grande", 4.0, 2.0)));
        indexador.indexar("spa0002", "Gracias es", "es", List.of(new TrechoLegenda("muchas gracias amigo", 4.0, 2.0)));
        indexador.indexar("rus0001", "Video ru", "ru", List.of(new TrechoLegenda("привет друзья", 4.0, 2.0)));
        // "no" existe em inglês e em espanhol: vale o idioma escolhido
        indexador.indexar("eng0002", "No en", "en", List.of(new TrechoLegenda("oh no", 4.0, 2.0)));
        indexador.indexar("spa0003", "No es", "es", List.of(new TrechoLegenda("pues no", 4.0, 2.0)));
    }

    @Test
    void deveBuscarSomenteNoIdiomaPedidoEListarOsIdiomasIndexados() {
        assertThat(buscaService.buscar("casa grande", "es").resultados())
                .extracting(ResultadoBusca::youtubeId).containsExactly("spa0001");
        assertThat(buscaService.buscar("casa grande", "en").resultados())
                .extracting(ResultadoBusca::youtubeId).containsExactly("eng0001");
        assertThat(buscaService.idiomas()).containsExactlyInAnyOrder("en", "es", "ru");
    }

    @Test
    void deveTrocarParaOIdiomaOndeAFraseExiste() {
        RespostaBusca resposta = buscaService.buscar("muchas gracias", "en");

        assertThat(resposta.idioma()).isEqualTo("es");
        assertThat(resposta.resultados()).extracting(ResultadoBusca::youtubeId).containsExactly("spa0002");
    }

    @Test
    void deveIrDiretoAoIdiomaDaEscrita() {
        RespostaBusca resposta = buscaService.buscar("привет", "en");

        assertThat(resposta.idioma()).isEqualTo("ru");
        assertThat(resposta.resultados()).extracting(ResultadoBusca::youtubeId).containsExactly("rus0001");
        assertThat(resposta.fraseTransliterada()).isEqualTo("privet");
    }

    @Test
    void deveManterOIdiomaEscolhidoQuandoAFraseExisteNosDois() {
        assertThat(buscaService.buscar("no", "en").idioma()).isEqualTo("en");
        assertThat(buscaService.buscar("no", "es").idioma()).isEqualTo("es");
    }

    @Test
    void deveManterOIdiomaEscolhidoQuandoAFraseNaoExisteEmLugarNenhum() {
        RespostaBusca resposta = buscaService.buscar("zzzz qqqq", "en");

        assertThat(resposta.idioma()).isEqualTo("en");
        assertThat(resposta.resultados()).isEmpty();
    }

    @Test
    void deveCairNoAproximadoDeOutroIdiomaComoUltimoRecurso() {
        RespostaBusca resposta = buscaService.buscar("muchas gracias mi amigo", "en");

        assertThat(resposta.idioma()).isEqualTo("es");
        assertThat(resposta.resultados()).isNotEmpty();
        assertThat(resposta.resultados()).allMatch(r -> !r.exata());
    }
}
