package com.lucasmanoel.ouvirfrase.ingestao.painel;

import com.lucasmanoel.ouvirfrase.ingestao.IngestaoInterrompidaException;
import com.lucasmanoel.ouvirfrase.ingestao.IngestaoProperties;
import com.lucasmanoel.ouvirfrase.ingestao.IngestaoService;
import com.lucasmanoel.ouvirfrase.ingestao.RelatorioIngestao;
import com.lucasmanoel.ouvirfrase.ingestao.legenda.FalhaNaBuscaDeLegendaException;
import com.lucasmanoel.ouvirfrase.ingestao.painel.PainelDeIngestao.Andamento;
import com.lucasmanoel.ouvirfrase.ingestao.painel.PainelDeIngestao.Pedido;
import com.lucasmanoel.ouvirfrase.ingestao.painel.PainelDeIngestao.Situacao;
import com.lucasmanoel.ouvirfrase.ingestao.youtube.DescobertaDeCanal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PainelDeIngestaoTest {

    private static final Path DADOS = Path.of("data");
    private static final Pedido PEDIDO = new Pedido("@TEDTalks", "en", 100, 15);

    @Mock private DescobertaDeCanal descoberta;
    @Mock private IngestaoService ingestao;

    private final List<Runnable> fila = new ArrayList<>();
    private PainelDeIngestao painel;

    @BeforeEach
    void preparar() {
        // guarda a tarefa em vez de rodar em outra thread: o teste decide quando ela executa
        painel = new PainelDeIngestao(descoberta, ingestao, new IngestaoProperties(DADOS, 0, 0), fila::add);
    }

    private void rodarTarefa() {
        fila.remove(0).run();
    }

    private static RelatorioIngestao relatorio(int adiados) {
        return new RelatorioIngestao(10, 5, 0, 0, adiados, Map.of());
    }

    @Test
    void deveComecarParadoERodandoDepoisDeIniciar() {
        assertThat(painel.andamento().situacao()).isEqualTo(Situacao.PARADO);

        painel.iniciar(PEDIDO);

        assertThat(painel.andamento().situacao()).isEqualTo(Situacao.RODANDO);
        assertThat(painel.andamento().pedido()).isEqualTo(PEDIDO);
    }

    @Test
    void deveDescobrirEIndexarSoOCanalPedidoMostrandoOProgresso() {
        when(descoberta.descobrir(DADOS, "@TEDTalks", "en", 100))
                .thenReturn(new DescobertaDeCanal.Resultado(DADOS.resolve("en").resolve("tedtalks.csv"), 10));
        when(ingestao.executar(eq("tedtalks"), eq(15), any())).thenAnswer(chamada -> {
            Consumer<String> progresso = chamada.getArgument(2);
            progresso.accept("Indexando 1 de 5: Titulo");
            assertThat(painel.andamento().mensagem()).isEqualTo("Indexando 1 de 5: Titulo");
            assertThat(painel.andamento().situacao()).isEqualTo(Situacao.RODANDO);
            return relatorio(3);
        });

        painel.iniciar(PEDIDO);
        rodarTarefa();

        Andamento fim = painel.andamento();
        assertThat(fim.situacao()).isEqualTo(Situacao.CONCLUIDO);
        assertThat(fim.adiados()).isEqualTo(3);
        assertThat(fim.relatorio()).contains("Relatório da ingestão").contains("Indexados agora: 5");
    }

    @Test
    void deveRecusarSegundaIngestaoEnquantoUmaRoda() {
        painel.iniciar(PEDIDO);

        assertThatExceptionOfType(PainelDeIngestao.EmAndamentoException.class)
                .isThrownBy(() -> painel.iniciar(PEDIDO));
        assertThat(fila).hasSize(1);
    }

    @Test
    void devePermitirNovaIngestaoDepoisDeTerminar() {
        when(descoberta.descobrir(DADOS, "@TEDTalks", "en", 100))
                .thenReturn(new DescobertaDeCanal.Resultado(DADOS.resolve("en").resolve("tedtalks.csv"), 0));
        when(ingestao.executar(eq("tedtalks"), anyInt(), any())).thenReturn(relatorio(0));
        painel.iniciar(PEDIDO);
        rodarTarefa();

        painel.iniciar(PEDIDO);

        assertThat(painel.andamento().situacao()).isEqualTo(Situacao.RODANDO);
    }

    @Test
    void deveExplicarOBloqueioDoYouTubeEGuardarORelatorioParcial() {
        when(descoberta.descobrir(DADOS, "@TEDTalks", "en", 100))
                .thenReturn(new DescobertaDeCanal.Resultado(DADOS.resolve("en").resolve("tedtalks.csv"), 10));
        when(ingestao.executar(eq("tedtalks"), eq(15), any()))
                .thenThrow(new IngestaoInterrompidaException(relatorio(0),
                        new FalhaNaBuscaDeLegendaException("IP bloqueado")));

        painel.iniciar(PEDIDO);
        rodarTarefa();

        Andamento fim = painel.andamento();
        assertThat(fim.situacao()).isEqualTo(Situacao.ERRO);
        assertThat(fim.mensagem()).contains("bloqueou este IP").contains("Continuar");
        assertThat(fim.relatorio()).contains("Indexados agora: 5");
    }

    @Test
    void deveMostrarOErroQuandoOCanalNaoExiste() {
        when(descoberta.descobrir(DADOS, "@TEDTalks", "en", 100))
                .thenThrow(new IllegalArgumentException("Canal não encontrado: @TEDTalks"));

        painel.iniciar(PEDIDO);
        rodarTarefa();

        assertThat(painel.andamento().situacao()).isEqualTo(Situacao.ERRO);
        assertThat(painel.andamento().mensagem()).isEqualTo("Canal não encontrado: @TEDTalks");
        assertThat(painel.andamento().relatorio()).isNull();
    }
}
