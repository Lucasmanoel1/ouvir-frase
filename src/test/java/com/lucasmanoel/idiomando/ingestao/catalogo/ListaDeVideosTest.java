package com.lucasmanoel.idiomando.ingestao.catalogo;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ListaDeVideosTest {

    @Test
    void deveTirarOIdiomaDaPastaEONomeDoArquivo() {
        ListaDeVideos lista = ListaDeVideos.doArquivo(Path.of("data", "zh-CN", "playlist-abc.csv"));

        assertThat(lista.idioma()).isEqualTo("zh-CN");
        assertThat(lista.nome()).isEqualTo("playlist-abc");
        assertThat(lista.playlist()).isTrue();
    }

    @Test
    void naoDeveTratarComoPlaylistUmNomeSemOPrefixo() {
        assertThat(ListaDeVideos.doArquivo(Path.of("data", "en", "ted.csv")).playlist()).isFalse();
    }
}
