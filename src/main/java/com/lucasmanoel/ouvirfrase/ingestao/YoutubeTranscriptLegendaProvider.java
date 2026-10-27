package com.lucasmanoel.ouvirfrase.ingestao;

import com.lucasmanoel.ouvirfrase.dto.TrechoLegenda;
import com.lucasmanoel.ouvirfrase.exception.FalhaNaBuscaDeLegendaException;
import io.github.thoroldvix.api.Transcript;
import io.github.thoroldvix.api.TranscriptContent;
import io.github.thoroldvix.api.TranscriptList;
import io.github.thoroldvix.api.TranscriptRetrievalException;
import io.github.thoroldvix.api.YoutubeTranscriptApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Profile("ingestao")
@RequiredArgsConstructor
@Slf4j
public class YoutubeTranscriptLegendaProvider implements LegendaProvider {

    private final YoutubeTranscriptApi api;

    @Override
    public Optional<List<TrechoLegenda>> buscar(String youtubeId, String idioma) {
        TranscriptList disponiveis = listar(youtubeId);

        Transcript manual;
        try {
            manual = disponiveis.findManualTranscript(idioma);
        } catch (TranscriptRetrievalException e) {
            log.warn("Vídeo [{}] sem legenda manual em '{}': {}", youtubeId, idioma, e.getMessage());
            return Optional.empty();
        }

        List<TrechoLegenda> trechos = converter(baixar(manual, youtubeId));
        if (trechos.isEmpty()) {
            log.warn("Vídeo [{}] com legenda manual vazia", youtubeId);
            return Optional.empty();
        }
        return Optional.of(trechos);
    }

    private TranscriptList listar(String youtubeId) {
        try {
            return api.listTranscripts(youtubeId);
        } catch (TranscriptRetrievalException e) {
            throw new FalhaNaBuscaDeLegendaException("Falha ao listar legendas de " + youtubeId, e);
        }
    }

    private TranscriptContent baixar(Transcript transcript, String youtubeId) {
        try {
            return transcript.fetch();
        } catch (TranscriptRetrievalException e) {
            throw new FalhaNaBuscaDeLegendaException("Falha ao baixar legenda de " + youtubeId, e);
        }
    }

    static List<TrechoLegenda> converter(TranscriptContent conteudo) {
        return conteudo.getContent().stream()
                .map(f -> new TrechoLegenda(f.getText(), f.getStart(), f.getDur()))
                .toList();
    }
}