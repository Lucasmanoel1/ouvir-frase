package com.lucasmanoel.ouvirfrase.ingestao;

import java.util.EnumMap;
import java.util.Map;

public record RelatorioIngestao(int total,
                                int indexados,
                                int jaIndexados,
                                int adiados,
                                Map<VideoSemente, MotivoDescarte> descartados) {

    public String resumo() {
        Map<MotivoDescarte, Integer> porMotivo = new EnumMap<>(MotivoDescarte.class);
        descartados.values().forEach(m -> porMotivo.merge(m, 1, Integer::sum));

        StringBuilder sb = new StringBuilder("Relatório da ingestão\n");
        sb.append("  Na lista: ").append(total).append('\n');
        sb.append("  Indexados agora: ").append(indexados).append('\n');
        sb.append("  Já indexados (pulados): ").append(jaIndexados).append('\n');
        if (adiados > 0) {
            sb.append("  Adiados pelo limite (rode de novo): ").append(adiados).append('\n');
        }
        sb.append("  Descartados: ").append(descartados.size()).append('\n');
        porMotivo.forEach((motivo, qtd) ->
                sb.append("    ").append(motivo).append(": ").append(qtd).append('\n'));
        descartados.forEach((video, motivo) ->
                sb.append("    - [").append(video.youtubeId()).append("] ")
                        .append(video.titulo()).append(" -> ").append(motivo).append('\n'));
        return sb.toString();
    }
}
