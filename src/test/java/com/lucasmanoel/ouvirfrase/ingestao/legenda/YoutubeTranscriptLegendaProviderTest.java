package com.lucasmanoel.ouvirfrase.ingestao.legenda;

import io.github.thoroldvix.api.Transcript;
import io.github.thoroldvix.api.TranscriptContent;
import io.github.thoroldvix.api.TranscriptList;
import io.github.thoroldvix.api.TranscriptRetrievalException;
import io.github.thoroldvix.api.YoutubeTranscriptApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YoutubeTranscriptLegendaProviderTest {

    private static final String ID = "AAAAAAAAAAA";

    @Mock private YoutubeTranscriptApi api;
    @Mock private TranscriptList lista;
    @Mock private Transcript transcript;

    private YoutubeTranscriptLegendaProvider provider;

    @BeforeEach
    void setup() {
        provider = new YoutubeTranscriptLegendaProvider(api);
    }

    @Test
    void deveConverterFragmentosEmTrechos() throws Exception {
        TranscriptContent conteudo = new TranscriptContent(List.of(
                new TranscriptContent.Fragment("hello world", 1.5, 2.0),
                new TranscriptContent.Fragment("how are you", 3.5, 1.0)));
        when(api.listTranscripts(ID)).thenReturn(lista);
        when(lista.findManualTranscript("en")).thenReturn(transcript);
        when(transcript.fetch()).thenReturn(conteudo);

        Optional<LegendaBaixada> resultado = provider.buscar(ID, "en");

        assertThat(resultado).contains(new LegendaBaixada(List.of(
                new TrechoLegenda("hello world", 1.5, 2.0),
                new TrechoLegenda("how are you", 3.5, 1.0)), false));
    }

    @Test
    void deveCairParaALegendaAutomaticaQuandoNaoHaManual() throws Exception {
        TranscriptContent conteudo = new TranscriptContent(List.of(
                new TranscriptContent.Fragment("hello world", 1.5, 2.0)));
        when(api.listTranscripts(ID)).thenReturn(lista);
        when(lista.findManualTranscript("en"))
                .thenThrow(new TranscriptRetrievalException(ID, "sem legenda manual"));
        when(lista.findGeneratedTranscript("en")).thenReturn(transcript);
        when(transcript.fetch()).thenReturn(conteudo);

        assertThat(provider.buscar(ID, "en")).contains(
                new LegendaBaixada(List.of(new TrechoLegenda("hello world", 1.5, 2.0)), true));
    }

    @Test
    void deveDevolverVazioQuandoNaoHaLegendaManualNemAutomatica() throws Exception {
        when(api.listTranscripts(ID)).thenReturn(lista);
        when(lista.findManualTranscript("en"))
                .thenThrow(new TranscriptRetrievalException(ID, "sem legenda manual"));
        when(lista.findGeneratedTranscript("en"))
                .thenThrow(new TranscriptRetrievalException(ID, "sem legenda automatica"));

        assertThat(provider.buscar(ID, "en")).isEmpty();
    }

    @Test
    void deveLancarFalhaQuandoListagemDeLegendasFalha() throws Exception {
        when(api.listTranscripts(ID))
                .thenThrow(new TranscriptRetrievalException(ID, "IP bloqueado"));

        assertThatExceptionOfType(FalhaNaBuscaDeLegendaException.class)
                .isThrownBy(() -> provider.buscar(ID, "en"));
    }

    @Test
    void deveBuscarATraducaoQuandoOVideoPermite() throws Exception {
        Transcript traduzida = org.mockito.Mockito.mock(Transcript.class);
        when(api.listTranscripts(ID)).thenReturn(lista);
        when(lista.findManualTranscript("en")).thenReturn(transcript);
        when(transcript.isTranslatable()).thenReturn(true);
        when(transcript.getTranslationLanguages()).thenReturn(java.util.Set.of("pt"));
        when(transcript.translate("pt")).thenReturn(traduzida);
        when(traduzida.fetch()).thenReturn(new TranscriptContent(List.of(
                new TranscriptContent.Fragment("olá", 1.5, 2.0))));

        assertThat(provider.buscarTraducao(ID, "en", "pt"))
                .contains(List.of(new TrechoLegenda("olá", 1.5, 2.0)));
    }

    @Test
    void deveDevolverVazioQuandoOIdiomaAlvoNaoEstaDisponivel() throws Exception {
        when(api.listTranscripts(ID)).thenReturn(lista);
        when(lista.findManualTranscript("en")).thenReturn(transcript);
        when(transcript.isTranslatable()).thenReturn(true);
        when(transcript.getTranslationLanguages()).thenReturn(java.util.Set.of("es"));

        assertThat(provider.buscarTraducao(ID, "en", "pt")).isEmpty();
    }
}
