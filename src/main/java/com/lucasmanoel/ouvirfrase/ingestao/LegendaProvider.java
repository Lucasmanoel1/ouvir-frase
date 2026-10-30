package com.lucasmanoel.ouvirfrase.ingestao;

import com.lucasmanoel.ouvirfrase.dto.TrechoLegenda;

import java.util.List;
import java.util.Optional;

public interface LegendaProvider {

    /** A legenda manual do vídeo ou, na falta dela, a automática; vazio se não houver nenhuma. */
    Optional<LegendaBaixada> buscar(String youtubeId, String idioma);

    /** A legenda manual do vídeo traduzida pelo YouTube; vazio se o vídeo não permite traduzir para o idioma. */
    Optional<List<TrechoLegenda>> buscarTraducao(String youtubeId, String idioma, String idiomaAlvo);
}
