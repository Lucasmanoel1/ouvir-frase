package com.lucasmanoel.ouvirfrase.ingestao;

public record VideoSemente(String youtubeId, String idioma, String titulo, String canal) {

    public VideoSemente(String youtubeId, String idioma, String titulo) {
        this(youtubeId, idioma, titulo, null);
    }
}
