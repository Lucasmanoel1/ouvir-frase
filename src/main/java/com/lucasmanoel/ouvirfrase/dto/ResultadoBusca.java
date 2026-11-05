package com.lucasmanoel.ouvirfrase.dto;

public record ResultadoBusca(String youtubeId, String titulo,
                             double inicioSeg,         // início estimado da 1ª palavra, sem recuo
                             double ultimaPalavraSeg,  // início estimado da última palavra (casada, na aproximada)
                             double falaInicioSeg,     // início do trecho da legenda da 1ª palavra
                             double falaFimSeg,        // fim do trecho da última palavra (início + duração, sem passar do início do seguinte)
                             String legenda, double pontuacao, boolean exata, boolean legendaAutomatica,
                             String legendaTransliterada) {
}
