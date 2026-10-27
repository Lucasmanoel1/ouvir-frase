package com.lucasmanoel.ouvirfrase.ingestao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("ingestao")
@RequiredArgsConstructor
@Slf4j
public class FiltroEmbeddable {

    private final YouTubeDataClient client;

    public ResultadoFiltro filtrar(List<VideoSemente> sementes) {
        if (sementes.isEmpty()) {
            return new ResultadoFiltro(List.of(), Map.of());
        }

        Map<String, Boolean> embeddablePorId = client.consultarEmbeddable(
                sementes.stream().map(VideoSemente::youtubeId).toList());

        List<VideoSemente> aprovados = new ArrayList<>();
        Map<VideoSemente, MotivoDescarte> descartados = new LinkedHashMap<>();

        for (VideoSemente semente : sementes) {
            Boolean embeddable = embeddablePorId.get(semente.youtubeId());
            if (embeddable == null) {
                descartar(descartados, semente, MotivoDescarte.NAO_ENCONTRADO);
            } else if (!embeddable) {
                descartar(descartados, semente, MotivoDescarte.NAO_INCORPORAVEL);
            } else {
                aprovados.add(semente);
            }
        }
        return new ResultadoFiltro(aprovados, descartados);
    }

    private void descartar(Map<VideoSemente, MotivoDescarte> descartados,
                           VideoSemente semente, MotivoDescarte motivo) {
        log.warn("Vídeo descartado [{}] {}: {}", semente.youtubeId(), semente.titulo(), motivo);
        descartados.put(semente, motivo);
    }
}