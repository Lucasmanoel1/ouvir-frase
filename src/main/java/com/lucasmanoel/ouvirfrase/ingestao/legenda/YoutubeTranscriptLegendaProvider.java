package com.lucasmanoel.ouvirfrase.ingestao.legenda;

import io.github.thoroldvix.api.Transcript;
import io.github.thoroldvix.api.TranscriptContent;
import io.github.thoroldvix.api.TranscriptList;
import io.github.thoroldvix.api.TranscriptRetrievalException;
import io.github.thoroldvix.api.YoutubeTranscriptApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.Optional;

@Component
@Profile("ingestao")
@RequiredArgsConstructor
@Slf4j
public class YoutubeTranscriptLegendaProvider implements LegendaProvider {

    private final YoutubeTranscriptApi api;

    @Override
    public Optional<LegendaBaixada> buscar(String youtubeId, String idioma) {
        TranscriptList disponiveis = listar(youtubeId);

        Transcript transcript;
        boolean automatica = false;
        try {
            transcript = disponiveis.findManualTranscript(idioma);
        } catch (TranscriptRetrievalException semManual) {
            try {
                transcript = disponiveis.findGeneratedTranscript(idioma);
                automatica = true;
            } catch (TranscriptRetrievalException semAutomatica) {
                log.warn("Vídeo [{}] sem legenda manual nem automática em '{}': {}",
                        youtubeId, idioma, semAutomatica.getMessage());
                return Optional.empty();
            }
        }

        List<TrechoLegenda> trechos = converter(baixar(transcript, youtubeId));
        if (trechos.isEmpty()) {
            log.warn("Vídeo [{}] com legenda vazia", youtubeId);
            return Optional.empty();
        }
        return Optional.of(new LegendaBaixada(trechos, automatica));
    }

    private TranscriptList listar(String youtubeId) {
        try {
            return api.listTranscripts(youtubeId);
        } catch (TranscriptRetrievalException e) {
            throw new FalhaNaBuscaDeLegendaException("Falha ao listar legendas de " + youtubeId + ": " + e.getMessage(), e);
        }
    }

    private TranscriptContent baixar(Transcript transcript, String youtubeId) {
        try {
            return transcript.fetch();
        } catch (TranscriptRetrievalException e) {
            throw new FalhaNaBuscaDeLegendaException("Falha ao baixar legenda de " + youtubeId + " (" + transcript.getLanguageCode() + "): " + e.getMessage(), e);
        }
    }

    static List<TrechoLegenda> converter(TranscriptContent conteudo) {
        return conteudo.getContent().stream()
                .map(f -> new TrechoLegenda(HtmlUtils.htmlUnescape(f.getText()), f.getStart(), f.getDur()))
                .toList();
    }
}