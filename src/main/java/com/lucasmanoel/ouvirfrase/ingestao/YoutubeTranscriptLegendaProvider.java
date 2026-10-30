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

    @Override
    public Optional<List<TrechoLegenda>> buscarTraducao(String youtubeId, String idioma, String idiomaAlvo) {
        TranscriptList disponiveis = listar(youtubeId);

        Transcript traduzida;
        try {
            Transcript manual = disponiveis.findManualTranscript(idioma);
            if (!manual.isTranslatable() || !manual.getTranslationLanguages().contains(idiomaAlvo)) {
                log.warn("Vídeo [{}] não pode ser traduzido para '{}'", youtubeId, idiomaAlvo);
                return Optional.empty();
            }
            traduzida = manual.translate(idiomaAlvo);
        } catch (TranscriptRetrievalException e) {
            log.warn("Vídeo [{}] sem tradução para '{}': {}", youtubeId, idiomaAlvo, e.getMessage());
            return Optional.empty();
        }

        List<TrechoLegenda> trechos = converter(baixar(traduzida, youtubeId));
        return trechos.isEmpty() ? Optional.empty() : Optional.of(trechos);
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