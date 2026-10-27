package com.lucasmanoel.ouvirfrase.ingestao;

import com.lucasmanoel.ouvirfrase.dto.TrechoLegenda;

import java.util.List;
import java.util.Optional;

public interface LegendaProvider {

    Optional<List<TrechoLegenda>> buscar(String youtubeId, String idioma);
}
