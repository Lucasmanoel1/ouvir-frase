package com.lucasmanoel.ouvirfrase.ingestao;

import java.util.List;
import java.util.Map;

public record ResultadoFiltro (List<VideoSemente> aprovados,
                               Map<VideoSemente, MotivoDescarte> descartados) {
}
