package com.lucasmanoel.ouvirfrase.ingestao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CatalogoDeVideosTest {

    @TempDir Path raiz;

    private final CatalogoDeVideos catalogo = new CatalogoDeVideos(new VideoCsvReader());

    private void csv(String idiomaPasta, String canal, String conteudo) throws IOException {
        Path pasta = Files.createDirectories(raiz.resolve(idiomaPasta));
        Files.writeString(pasta.resolve(canal + ".csv"), "youtube_id,idioma,titulo\n" + conteudo);
    }

    @Test
    void deveLerTodasAsPastasAtribuindoCanalEIgnorandoRepetidoEntreArquivos() throws IOException {
        csv("en", "ted", "AAAAAAAAAAA,en,Um\nBBBBBBBBBBB,en,Dois\n");
        csv("es", "canal-es", "CCCCCCCCCCC,es,Tres\nAAAAAAAAAAA,es,Repetido\n");

        List<VideoSemente> videos = catalogo.carregar(raiz);

        assertThat(videos).extracting(VideoSemente::youtubeId, VideoSemente::idioma, VideoSemente::canal)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("AAAAAAAAAAA", "en", "ted"),
                        org.assertj.core.groups.Tuple.tuple("BBBBBBBBBBB", "en", "ted"),
                        org.assertj.core.groups.Tuple.tuple("CCCCCCCCCCC", "es", "canal-es"));
    }

    @Test
    void deveFalharQuandoOIdiomaDoCsvNaoBateComAPasta() throws IOException {
        csv("en", "ted", "AAAAAAAAAAA,es,Um\n");

        assertThatIllegalArgumentException().isThrownBy(() -> catalogo.carregar(raiz))
                .withMessageContaining("não bate com a pasta");
    }
}
