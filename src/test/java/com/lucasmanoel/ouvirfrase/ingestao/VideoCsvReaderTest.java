package com.lucasmanoel.ouvirfrase.ingestao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VideoCsvReaderTest {

    private final VideoCsvReader leitor = new VideoCsvReader();

    @TempDir
    Path pasta;

    @Test
    void deveLerVideosIgnorandoComentariosELinhasVazias() throws IOException {
        Path csv = pasta.resolve("videos.csv");
        Files.writeString(csv, """
                youtube_id,idioma,titulo
                # TED
                AAAAAAAAAAA,en,"Titulo, com virgula"

                BBBBBBBBBBB,en,Outro titulo
                """);

        List<VideoSemente> resultado = leitor.ler(csv);

        assertThat(resultado).containsExactly(
                new VideoSemente("AAAAAAAAAAA", "en", "Titulo, com virgula"),
                new VideoSemente("BBBBBBBBBBB", "en", "Outro titulo"));
    }
}
