package com.lucasmanoel.ouvirfrase.ingestao;

import com.lucasmanoel.ouvirfrase.ingestao.catalogo.VideoSemente;

import java.util.List;
import java.util.Map;

public record ResultadoFiltro (List<VideoSemente> aprovados,
                               Map<VideoSemente, MotivoDescarte> descartados) {
}
