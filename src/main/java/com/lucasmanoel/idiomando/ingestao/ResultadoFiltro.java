package com.lucasmanoel.idiomando.ingestao;

import com.lucasmanoel.idiomando.ingestao.catalogo.VideoSemente;

import java.util.List;
import java.util.Map;

public record ResultadoFiltro (List<VideoSemente> aprovados,
                               Map<VideoSemente, MotivoDescarte> descartados) {
}
