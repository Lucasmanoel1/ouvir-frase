package com.lucasmanoel.ouvirfrase.ingestao.painel;

import com.lucasmanoel.ouvirfrase.ingestao.painel.PainelDeIngestao.Andamento;
import com.lucasmanoel.ouvirfrase.ingestao.painel.PainelDeIngestao.Pedido;
import com.lucasmanoel.ouvirfrase.ingestao.painel.PainelDeIngestao.Situacao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test
    void deveIniciarComOsPadroesQuandoSoOCanalVem() throws Exception {
        Pedido esperado = new Pedido("@TEDTalks", "en", 100, 15);
        when(painel.andamento())
                .thenReturn(new Andamento(Situacao.RODANDO, "Procurando os vídeos do canal…", null, 0, esperado));

        mockMvc.perform(post("/api/painel/ingestao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canal\":\" @TEDTalks \"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.situacao").value("RODANDO"))
                .andExpect(jsonPath("$.pedido.canal").value("@TEDTalks"));

        verify(painel).iniciar(esperado);
    }

    @Test
    void deveRepassarOsValoresInformados() throws Exception {
        mockMvc.perform(post("/api/painel/ingestao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canal\":\"https://www.youtube.com/@vox\",\"idioma\":\"pt-BR\",\"max\":30,\"limite\":2}"))
                .andExpect(status().isAccepted());

        verify(painel).iniciar(new Pedido("https://www.youtube.com/@vox", "pt-BR", 30, 2));
    }

    @Test
    void deveRejeitarCanalEmBranco() throws Exception {
        mockMvc.perform(post("/api/painel/ingestao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canal\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("requisicao_invalida"))
                .andExpect(jsonPath("$.mensagem").isNotEmpty());

        verify(painel, never()).iniciar(any());
    }

    @Test
    void deveRejeitarIdiomaQueNaoEUmCodigo() throws Exception {
        mockMvc.perform(post("/api/painel/ingestao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canal\":\"@TEDTalks\",\"idioma\":\"../x\"}"))
                .andExpect(status().isBadRequest());

        verify(painel, never()).iniciar(any());
    }

    @Test
    void deveRejeitarLoteForaDoIntervalo() throws Exception {
        mockMvc.perform(post("/api/painel/ingestao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canal\":\"@TEDTalks\",\"limite\":500}"))
                .andExpect(status().isBadRequest());

        verify(painel, never()).iniciar(any());
    }

    @Test
    void deveResponderConflitoQuandoJaHaIngestaoRodando() throws Exception {
        doThrow(new PainelDeIngestao.EmAndamentoException()).when(painel).iniciar(any());

        mockMvc.perform(post("/api/painel/ingestao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canal\":\"@TEDTalks\"}"))
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
                new Pedido("@TEDTalks", "en", 100, 15)));

        mockMvc.perform(get("/api/painel/ingestao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CONCLUIDO"))
                .andExpect(jsonPath("$.adiados").value(4))
                .andExpect(jsonPath("$.relatorio").value("Relatório da ingestão"));
    }
}
