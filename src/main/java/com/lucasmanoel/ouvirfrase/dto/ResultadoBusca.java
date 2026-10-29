package com.lucasmanoel.ouvirfrase.dto;

public record ResultadoBusca(String youtubeId, String titulo, double inicioSeg, String legenda,
                             double pontuacao, boolean exata) {
}
