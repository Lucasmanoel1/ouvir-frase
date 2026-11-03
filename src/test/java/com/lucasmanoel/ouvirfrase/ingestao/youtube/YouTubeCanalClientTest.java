package com.lucasmanoel.ouvirfrase.ingestao.youtube;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class YouTubeCanalClientTest {

    private MockRestServiceServer servidor;
    private YouTubeCanalClient client;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.teste/youtube/v3");
        servidor = MockRestServiceServer.bindTo(builder).build();
        client = new YouTubeCanalClient(new YouTubeApi(builder.build()));
    }

    private void esperarPlaylist(String id, String resposta) {
        servidor.expect(requestTo(startsWith("https://api.teste/youtube/v3/playlists")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("part", "snippet"))
                .andExpect(queryParam("id", id))
                .andRespond(withSuccess(resposta, MediaType.APPLICATION_JSON));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/playlist?list=PLabc",
            "https://www.youtube.com/watch?v=AAAAAAAAAAA&list=PLabc"})
    void deveResolverPlaylistPeloParametroList(String link) {
        esperarPlaylist("PLabc", "{\"items\":[{\"snippet\":{\"title\":\"Ted Talks Daily\"}}]}");

        YouTubeCanalClient.Fonte fonte = client.resolverFonte(link);

        assertThat(fonte).isEqualTo(new YouTubeCanalClient.Fonte("Ted Talks Daily", "PLabc", "playlist-ted-talks-daily"));
        servidor.verify();
    }

    @Test
    void deveUsarOIdDaPlaylistQuandoOTituloNaoTemLetraAscii() {
        esperarPlaylist("PLabc", "{\"items\":[{\"snippet\":{\"title\":\"Привет\"}}]}");

        assertThat(client.resolverFonte("https://www.youtube.com/playlist?list=PLabc").nomeArquivo())
                .isEqualTo("playlist-plabc");
    }

    @Test
    void deveNomearOCanalPeloHandle() {
        servidor.expect(requestTo(startsWith("https://api.teste/youtube/v3/channels")))
                .andExpect(queryParam("part", "snippet,contentDetails"))
                .andExpect(queryParam("forHandle", "TEDTalks"))
                .andRespond(withSuccess("""
                        {"items":[{"id":"UCabcdefghijklmnopqrstuv","snippet":{"title":"TED"},
                          "contentDetails":{"relatedPlaylists":{"uploads":"UUted"}}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.resolverFonte("https://www.youtube.com/@TEDTalks"))
                .isEqualTo(new YouTubeCanalClient.Fonte("TED", "UUted", "tedtalks"));
    }

    @Test
    void deveUsarOIdDoCanalQuandoOTituloNaoTemLetraAscii() {
        servidor.expect(requestTo(startsWith("https://api.teste/youtube/v3/channels")))
                .andExpect(queryParam("id", "UCabcdefghijklmnopqrstuv"))
                .andRespond(withSuccess("""
                        {"items":[{"id":"UCabcdefghijklmnopqrstuv","snippet":{"title":"Канал"},
                          "contentDetails":{"relatedPlaylists":{"uploads":"UUcanal"}}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.resolverFonte("UCabcdefghijklmnopqrstuv").nomeArquivo())
                .isEqualTo("ucabcdefghijklmnopqrstuv");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=AAAAAAAAAAA&list=RDAAAAAAAAAAA",
            "https://www.youtube.com/playlist?list=WL",
            "https://www.youtube.com/playlist?list=LL",
            "https://www.youtube.com/playlist?list=LM"})
    void deveRecusarMixEListaPessoalSemChamarAApi(String link) {
        assertThatIllegalArgumentException().isThrownBy(() -> client.resolverFonte(link))
                .withMessageContaining("Use uma playlist pública");

        servidor.verify(); // nenhuma requisição esperada: qualquer chamada à API teria falhado
    }

    @Test
    void deveFalharQuandoAPlaylistNaoExisteOuEPrivada() {
        esperarPlaylist("PLabc", "{}");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> client.resolverFonte("https://www.youtube.com/playlist?list=PLabc"))
                .withMessageContaining("Playlist não encontrada ou privada");
    }
}
