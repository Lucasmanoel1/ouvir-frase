package com.lucasmanoel.ouvirfrase.ingestao.legenda;

import java.util.Optional;

public interface LegendaProvider {

    /** A legenda manual do vídeo ou, na falta dela, a automática; vazio se não houver nenhuma. */
    Optional<LegendaBaixada> buscar(String youtubeId, String idioma);
}
