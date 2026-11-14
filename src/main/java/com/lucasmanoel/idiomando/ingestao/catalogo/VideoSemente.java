package com.lucasmanoel.idiomando.ingestao.catalogo;

public record VideoSemente(String youtubeId, String idioma, String titulo, String canal) {

    public VideoSemente(String youtubeId, String idioma, String titulo) {
        this(youtubeId, idioma, titulo, null);
    }

    public ListaDeVideos lista() {
        return new ListaDeVideos(idioma, canal);
    }
}
