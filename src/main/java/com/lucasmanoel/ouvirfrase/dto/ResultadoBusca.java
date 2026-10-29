package com.lucasmanoel.ouvirfrase.dto;

public record ResultadoBusca(String youtubeId, String titulo, double inicioSeg, double fimSeg, String legenda,
                             double pontuacao, boolean exata) {
}
