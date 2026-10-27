package com.lucasmanoel.ouvirfrase.ingestao;

import com.lucasmanoel.ouvirfrase.exception.YouTubeApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class YouTubeDataClientTest {

    private MockRestServiceServer servidor;
    private YouTubeDataClient client;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.teste/youtube/v3");
        servidor = MockRestServiceServer.bindTo(builder).build();
        client = new YouTubeDataClient(builder.build());
    }

    @Test
    void deveDevolverEmbeddablePorIdEIgnorarCamposDesconhecidos() {
        servidor.expect(requestTo(startsWith("https://api.teste/youtube/v3/videos")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("part", "status"))
                .andExpect(queryParam("id", "AAAAAAAAAAA,BBBBBBBBBBB"))
                .andRespond(withSuccess("""
                        {
                          "kind": "youtube#videoListResponse",
                          "etag": "xyz",
                          "items": [
                            {"id": "AAAAAAAAAAA", "kind": "youtube#video",
                             "status": {"embeddable": true, "privacyStatus": "public"}},
                            {"id": "BBBBBBBBBBB",
                             "status": {"embeddable": false, "privacyStatus": "public"}}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        Map<String, Boolean> resultado =
                client.consultarEmbeddable(List.of("AAAAAAAAAAA", "BBBBBBBBBBB"));

        assertThat(resultado)
                .containsEntry("AAAAAAAAAAA", true)
                .containsEntry("BBBBBBBBBBB", false)
                .hasSize(2);

        servidor.verify();
    }

    @Test
    void naoDeveIncluirNoMapaIdQueAApiNaoDevolveu() {
        servidor.expect(requestTo(startsWith("https://api.teste/youtube/v3/videos")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("part", "status"))
                .andExpect(queryParam("id", "AAAAAAAAAAA,BBBBBBBBBBB"))
                .andRespond(withSuccess("""
                        {
                          "kind": "youtube#videoListResponse",
                          "etag": "xyz",
                          "items": [
                            {"id": "AAAAAAAAAAA", "kind": "youtube#video",
                             "status": {"embeddable": true, "privacyStatus": "public"}},
                            {"id": "", "kind": "youtube#video",
                             "status": {"embeddable": false, "privacyStatus": "public"}}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        Map<String, Boolean> resultado =
                client.consultarEmbeddable(List.of("AAAAAAAAAAA", "BBBBBBBBBBB"));

        assertThat(resultado)
                .containsEntry("AAAAAAAAAAA", true)
                .hasSize(1);

        servidor.verify();
    }

    @Test
    void deveDevolverMapaVazioQuandoItemsAusente() {
        servidor.expect(requestTo(startsWith("https://api.teste/youtube/v3/videos")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("part", "status"))
                .andExpect(queryParam("id", "AAAAAAAAAAA,BBBBBBBBBBB"))
                .andRespond(withSuccess("""
                        {
                          "kind": "youtube#videoListResponse",
                          "etag": "xyz"
                        }
                        """, MediaType.APPLICATION_JSON));


        Map<String, Boolean> resultado =
                client.consultarEmbeddable(List.of("AAAAAAAAAAA", "BBBBBBBBBBB"));

        assertThat(resultado)
                .isEmpty();

        servidor.verify();
    }

    @Test
    void deveFazerTresChamadasParaCentoEVinteIds() {
        List<String> todososIds = IntStream.range(0, 120)
                .mapToObj(i -> "id" + i).toList();

        List<String> lote1 = todososIds.subList(0, 50);
        List<String> lote2 = todososIds.subList(50, 100);
        List<String> lote3 = todososIds.subList(100, 120);

        servidor.expect(requestTo(startsWith("https://api.teste/youtube/v3/videos")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("part", "status"))
                .andExpect(queryParam("id", String.join(",", lote1)))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {"id": "id0", "kind": "youtube#video",
                             "status": {"embeddable": true, "privacyStatus": "public"}}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        servidor.expect(requestTo(startsWith("https://api.teste/youtube/v3/videos")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("part", "status"))
                .andExpect(queryParam("id", String.join(",", lote2)))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {"id": "id50", "kind": "youtube#video",
                             "status": {"embeddable": false, "privacyStatus": "public"}}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        servidor.expect(requestTo(startsWith("https://api.teste/youtube/v3/videos")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("part", "status"))
                .andExpect(queryParam("id", String.join(",", lote3)))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {"id": "id100", "kind": "youtube#video",
                             "status": {"embeddable": true, "privacyStatus": "public"}}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        Map<String, Boolean> resultado =
                client.consultarEmbeddable(todososIds);

        assertThat(resultado)
                .containsEntry("id0", true)
                .containsEntry("id50", false)
                .containsEntry("id100", true)
                .hasSize(3);
        servidor.verify();
    }

    @Test
    void naoDeveEntrarDuplicatasNaChamadaHttp() {
        servidor.expect(requestTo(startsWith("https://api.teste/youtube/v3/videos")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("part", "status"))
                .andExpect(queryParam("id", "AAAAAAAAAAA,BBBBBBBBBBB"))
                .andRespond(withSuccess("""
                        {
                          "kind": "youtube#videoListResponse",
                          "etag": "xyz",
                          "items": [
                            {"id": "AAAAAAAAAAA", "kind": "youtube#video",
                             "status": {"embeddable": true, "privacyStatus": "public"}},
                            {"id": "BBBBBBBBBBB",
                             "status": {"embeddable": true, "privacyStatus": "public"}}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        Map<String, Boolean> resultado =
                client.consultarEmbeddable(List.of("AAAAAAAAAAA", "BBBBBBBBBBB", "AAAAAAAAAAA"));

        assertThat(resultado)
                .hasSize(2);

        servidor.verify();
    }

    @Test
    void deveLancarYoutubeApiExceptionComErro500() {
        List<String> todososIds = IntStream.range(0, 120)
                .mapToObj(i -> "id" + i).toList();

        List<String> lote1 = todososIds.subList(0, 50);
        List<String> lote2 = todososIds.subList(50, 100);

        servidor.expect(requestTo(startsWith("https://api.teste/youtube/v3/videos")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("part", "status"))
                .andExpect(queryParam("id", String.join(",", lote1)))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {"id": "id0", "kind": "youtube#video",
                             "status": {"embeddable": true, "privacyStatus": "public"}}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        servidor.expect(requestTo(startsWith("https://api.teste/youtube/v3/videos")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("part", "status"))
                .andExpect(queryParam("id", String.join(",", lote2)))
                .andRespond(withServerError());

        assertThatExceptionOfType(YouTubeApiException.class).isThrownBy(
                () -> client.consultarEmbeddable(todososIds)).withMessageContaining("500");

        servidor.verify();
    }

    @Test
    void deveLancarYoutubeApiExceptionComErro403() {
        servidor.expect(requestTo(startsWith("https://api.teste/youtube/v3/videos")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("part", "status"))
                .andExpect(queryParam("id", "AAAAAAAAAAA"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatExceptionOfType(YouTubeApiException.class).isThrownBy(
                () -> client.consultarEmbeddable(List.of("AAAAAAAAAAA"))).withMessageContaining("403");

        servidor.verify();
    }

}