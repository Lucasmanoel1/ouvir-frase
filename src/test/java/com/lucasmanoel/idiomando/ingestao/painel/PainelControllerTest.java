package com.lucasmanoel.idiomando.ingestao.painel;

import com.lucasmanoel.idiomando.ingestao.IngestaoService;
import com.lucasmanoel.idiomando.ingestao.ResumoLista;
import com.lucasmanoel.idiomando.ingestao.painel.PainelDeIngestao.Andamento;
import com.lucasmanoel.idiomando.ingestao.painel.PainelDeIngestao.Pedido;
import com.lucasmanoel.idiomando.ingestao.painel.PainelDeIngestao.Situacao;
import com.lucasmanoel.idiomando.ingestao.painel.sugestoes.GeradorDeSugestoes;
import com.lucasmanoel.idiomando.ingestao.painel.sugestoes.Sugestao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PainelController.class)
@ActiveProfiles("painel")
class PainelControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PainelDeIngestao painel;
    @MockitoBean IngestaoService ingestao;
    @MockitoBean GeradorDeSugestoes gerador;

    @Test
    void deveIniciarComOsPadroesQuandoSoOLinkVem() throws Exception {
        Pedido esperado = new Pedido("@TEDTalks", "en", null, 100, 15);
        when(painel.andamento())
                .thenReturn(new Andamento(Situacao.RODANDO, "Procurando os vídeos…", null, 0, esperado, null));

        mockMvc.perform(post("/api/painel/ingestao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"link\":\" @TEDTalks \"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.situacao").value("RODANDO"))
                .andExpect(jsonPath("$.pedido.link").value("@TEDTalks"));

        verify(painel).iniciar(esperado);
    }

    @Test
    void deveRepassarOsValoresInformados() throws Exception {
        mockMvc.perform(post("/api/painel/ingestao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"link\":\"https://www.youtube.com/@vox\",\"idioma\":\"pt-BR\",\"max\":30,\"limite\":2}"))
                .andExpect(status().isAccepted());

        verify(painel).iniciar(new Pedido("https://www.youtube.com/@vox", "pt-BR", null, 30, 2));
    }

    @Test
    void deveRejeitarLinkEmBranco() throws Exception {
        mockMvc.perform(post("/api/painel/ingestao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"link\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("requisicao_invalida"))
                .andExpect(jsonPath("$.mensagem").isNotEmpty());

        verify(painel, never()).iniciar(any());
    }

    @Test
    void deveRejeitarIdiomaQueNaoEUmCodigo() throws Exception {
        mockMvc.perform(post("/api/painel/ingestao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"link\":\"@TEDTalks\",\"idioma\":\"../x\"}"))
                .andExpect(status().isBadRequest());

        verify(painel, never()).iniciar(any());
    }

    @Test
    void deveRejeitarLoteForaDoIntervalo() throws Exception {
        mockMvc.perform(post("/api/painel/ingestao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"link\":\"@TEDTalks\",\"limite\":500}"))
                .andExpect(status().isBadRequest());

        verify(painel, never()).iniciar(any());
    }

    @Test
    void deveResponderConflitoQuandoJaHaIngestaoRodando() throws Exception {
        doThrow(new PainelDeIngestao.EmAndamentoException()).when(painel).iniciar(any());

        mockMvc.perform(post("/api/painel/ingestao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"link\":\"@TEDTalks\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("em_andamento"));
    }

    @Test
    void deveExigirJson() throws Exception {
        mockMvc.perform(post("/api/painel/ingestao")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("@TEDTalks"))
                .andExpect(status().isUnsupportedMediaType());

        verify(painel, never()).iniciar(any());
    }

    @Test
    void deveDevolverOAndamento() throws Exception {
        when(painel.andamento()).thenReturn(new Andamento(Situacao.CONCLUIDO, "Pronto.", "Relatório da ingestão", 4,
                new Pedido("@TEDTalks", "en", null, 100, 15), null));

        mockMvc.perform(get("/api/painel/ingestao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CONCLUIDO"))
                .andExpect(jsonPath("$.adiados").value(4))
                .andExpect(jsonPath("$.relatorio").value("Relatório da ingestão"));
    }

    @Test
    void deveSoIndexarQuandoVemALista() throws Exception {
        mockMvc.perform(post("/api/painel/ingestao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"link\":\"https://www.youtube.com/@x\",\"idioma\":\"ru\",\"lista\":\"qwrtru\",\"max\":30}"))
                .andExpect(status().isAccepted());

        verify(painel).iniciar(new Pedido(null, "ru", "qwrtru", 100, 15));
    }

    @Test
    void deveRejeitarListaInvalida() throws Exception {
        mockMvc.perform(post("/api/painel/ingestao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idioma\":\"ru\",\"lista\":\"../x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Lista inválida."));

        verify(painel, never()).iniciar(any());
    }

    @Test
    void deveRejeitarPedidoSemLinkENemLista() throws Exception {
        mockMvc.perform(post("/api/painel/ingestao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idioma\":\"en\"}"))
                .andExpect(status().isBadRequest());

        verify(painel, never()).iniciar(any());
    }

    @Test
    void deveListarAsListasCadastradas() throws Exception {
        when(ingestao.listas()).thenReturn(List.of(new ResumoLista("en", "ted", false, 208, 43, 2, 163)));

        mockMvc.perform(get("/api/painel/listas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idioma").value("en"))
                .andExpect(jsonPath("$[0].nome").value("ted"))
                .andExpect(jsonPath("$[0].playlist").value(false))
                .andExpect(jsonPath("$[0].total").value(208))
                .andExpect(jsonPath("$[0].indexados").value(43))
                .andExpect(jsonPath("$[0].descartados").value(2))
                .andExpect(jsonPath("$[0].pendentes").value(163));
    }

    @Test
    void deveResponderErroQuandoOCatalogoNaoPodeSerLido() throws Exception {
        when(ingestao.listas()).thenThrow(new IllegalArgumentException("idioma 'es' não bate com a pasta 'en'"));

        mockMvc.perform(get("/api/painel/listas"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.erro").value("listas_ilegiveis"))
                .andExpect(jsonPath("$.mensagem").isNotEmpty());
    }

    @Test
    void deveGerarAsSugestoesDoIdiomaEDoMinimoPedidos() throws Exception {
        when(gerador.gerar("ru", 3)).thenReturn(List.of(new Sugestao("потому что", 5, 9)));

        mockMvc.perform(get("/api/painel/sugestoes").param("idioma", "ru").param("minimo", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].frase").value("потому что"))
                .andExpect(jsonPath("$[0].videos").value(5))
                .andExpect(jsonPath("$[0].vezes").value(9));

        verify(gerador).gerar("ru", 3);
    }

    @Test
    void deveUsarInglesEMinimoSeisQuandoNaoVemParametro() throws Exception {
        mockMvc.perform(get("/api/painel/sugestoes"))
                .andExpect(status().isOk());

        verify(gerador).gerar("en", 6);
    }

    @ParameterizedTest
    @CsvSource({"en,0", "en,101", "../x,6"})
    void deveRejeitarSugestoesComParametroInvalido(String idioma, int minimo) throws Exception {
        mockMvc.perform(get("/api/painel/sugestoes").param("idioma", idioma).param("minimo", String.valueOf(minimo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("requisicao_invalida"));

        verifyNoInteractions(gerador);
    }
}
