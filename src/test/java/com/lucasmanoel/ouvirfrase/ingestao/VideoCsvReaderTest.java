package com.lucasmanoel.ouvirfrase.ingestao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class VideoCsvReaderTest {

    private final VideoCsvReader leitor = new VideoCsvReader();

    @TempDir
    Path pasta;

    private Path criarCsv(String conteudo) throws IOException {
        Path csv = pasta.resolve("videos.csv");
        Files.writeString(csv, conteudo);
        return csv;
    }

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

    @Test
    void deveRejeitarYoutubeIdComTamanhoErrado() throws IOException {
        Path csv = criarCsv("""
            youtube_id,idioma,titulo
            ABC12,en,Titulo
            """);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> leitor.ler(csv))
                .withMessageContaining("youtube_id inválido");
    }

    @Test
    void deveRejeitarUrlInteira() throws IOException {
        Path csv = criarCsv("""
            youtube_id,idioma,titulo
            https://www.youtube.com/watch?v=QkF3oxziUI4&list=RD9OFpfTd0EIs&index=5,en,Titulo-exemplo
            """);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> leitor.ler(csv))
                .withMessageContaining("youtube_id inválido");
    }

    @Test
    void deveRejeitarYoutubeIdRepetido() throws IOException {
        Path csv = criarCsv("""
            youtube_id,idioma,titulo
            AAAAAAAAAAA,en,Primeiro
            AAAAAAAAAAA,en,Segundo
            """);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> leitor.ler(csv))
                .withMessageContaining("youtube_id repetido: ");
    }

    @Test
    void deveLerOArquivoRealDeVideos() {
        List<VideoSemente> videos = leitor.ler(Path.of("data/videos.csv"));

        assertThat(videos).isNotEmpty();
    }

    @Test
    void deveRejeitarLinhaComColunasFaltando() throws IOException {
        Path csv = criarCsv("""
            youtube_id,idioma,titulo
            AAAAAAAAAAA,en
            """);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> leitor.ler(csv));
    }
    @Test
    void deveRejeitarComTituloVazio() throws IOException {
        Path csv = criarCsv("""
            youtube_id,idioma,titulo
            AAAAAAAAAAA,en,
            """);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> leitor.ler(csv));
    }

    @Test
    void deveLancarUncheckedIOExceptionQuandoArquivoNaoExiste(){
        Path inexistente = pasta.resolve("sem-videos.csv");

        assertThatExceptionOfType(UncheckedIOException.class).isThrownBy(() -> leitor.ler(inexistente));
    }

    @Test
    void deveDevolverListaVaziaQuandoSoTemCabecalho() throws IOException {
        Path csv = criarCsv("""
            youtube_id,idioma,titulo
            """);

        assertThat(leitor.ler(csv)).isEmpty();
    }
}
