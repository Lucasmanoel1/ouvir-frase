package com.lucasmanoel.idiomando.ingestao.catalogo;

import java.nio.file.Path;

/**
 * Uma lista de vídeos = um CSV em {@code <dados>/<idioma>/<nome>.csv}.
 * O idioma vem da pasta, o nome vem do arquivo (sem a extensão). Playlists têm o prefixo {@code playlist-}.
 */
public record ListaDeVideos(String idioma, String nome) {

    public static final String PREFIXO_PLAYLIST = "playlist-";

    public static ListaDeVideos doArquivo(Path csv) {
        String arquivo = csv.getFileName().toString();
        return new ListaDeVideos(csv.getParent().getFileName().toString(),
                arquivo.substring(0, arquivo.length() - ".csv".length()));
    }

    public boolean playlist() {
        return nome != null && nome.startsWith(PREFIXO_PLAYLIST);
    }
}
