package com.lucasmanoel.ouvirfrase.ingestao.youtube;

import com.lucasmanoel.ouvirfrase.ingestao.catalogo.CatalogoDeVideos;
import com.lucasmanoel.ouvirfrase.ingestao.catalogo.VideoCsvReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DescobertaDeCanalTest {

    @TempDir Path raiz;
    @Mock YouTubeCanalClient client;

    @Test
    void deveGravarCsvDoCanalSemRepetirVideosJaConhecidos() throws IOException {
        Files.createDirectories(raiz.resolve("en"));
        Files.writeString(raiz.resolve("en").resolve("ted.csv"),
                "youtube_id,idioma,titulo\nAAAAAAAAAAA,en,Ja existe\n");
        DescobertaDeCanal descoberta = new DescobertaDeCanal(client, new CatalogoDeVideos(new VideoCsvReader()));

        when(client.resolverCanal("@Novo"))
                .thenReturn(new YouTubeCanalClient.Canal("Novo Canal", "UUplaylist", "novo"));
        when(client.listarVideos("UUplaylist", Set.of("AAAAAAAAAAA"), 5))
                .thenReturn(List.of(new YouTubeCanalClient.VideoDoCanal("BBBBBBBBBBB", "Titulo, com virgula")));

        DescobertaDeCanal.Resultado r = descoberta.descobrir(raiz, "@Novo", "en", 5);

        assertThat(r.gravados()).isEqualTo(1);
        assertThat(Files.readAllLines(raiz.resolve("en").resolve("novo.csv")))
                .containsExactly("youtube_id,idioma,titulo", "BBBBBBBBBBB,en,\"Titulo, com virgula\"");
    }
}
