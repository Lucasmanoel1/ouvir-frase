package com.lucasmanoel.ouvirfrase.ingestao;

import com.lucasmanoel.ouvirfrase.dto.TrechoLegenda;

import java.util.List;
import java.util.Optional;

public interface LegendaProvider {

    Optional<List<TrechoLegenda>> buscar(String youtubeId, String idioma);

    /** A legenda manual do vídeo traduzida pelo YouTube; vazio se o vídeo não permite traduzir para o idioma. */
    Optional<List<TrechoLegenda>> buscarTraducao(String youtubeId, String idioma, String idiomaAlvo);
}
