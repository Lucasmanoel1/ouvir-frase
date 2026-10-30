package com.lucasmanoel.ouvirfrase.ingestao.painel;

import com.lucasmanoel.ouvirfrase.ingestao.IngestaoInterrompidaException;
import com.lucasmanoel.ouvirfrase.ingestao.IngestaoProperties;
import com.lucasmanoel.ouvirfrase.ingestao.IngestaoService;
import com.lucasmanoel.ouvirfrase.ingestao.RelatorioIngestao;
import com.lucasmanoel.ouvirfrase.ingestao.legenda.FalhaNaBuscaDeLegendaException;
import com.lucasmanoel.ouvirfrase.ingestao.youtube.DescobertaDeCanal;
import com.lucasmanoel.ouvirfrase.ingestao.youtube.YouTubeApiException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Roda a ingestão de um canal em segundo plano, uma por vez, e guarda o andamento para a página consultar.
 * Um clique = descobrir os vídeos do canal (grava o CSV) + indexar um lote desse canal.
 * Só existe com o profile "painel" (uso local; nunca vai para produção).
 */
@Service
@Profile("painel")
@Slf4j
public class PainelDeIngestao {

    public enum Situacao { PARADO, RODANDO, CONCLUIDO, ERRO }

    /** O que o Lucas pediu na tela. */
    public record Pedido(String canal, String idioma, int max, int limite) {
    }

    /**
     * @param mensagem frase curta para a tela (etapa em curso, resultado ou o que deu errado)
     * @param relatorio texto do relatório da ingestão, quando houver
     * @param adiados  vídeos aprovados que ficaram para o próximo lote (> 0 = mostrar "Continuar")
     */
    public record Andamento(Situacao situacao, String mensagem, String relatorio, int adiados, Pedido pedido) {
    }

    public static class EmAndamentoException extends RuntimeException {
        public EmAndamentoException() {
            super("Já existe uma ingestão em andamento.");
        }
    }

    private final DescobertaDeCanal descoberta;
    private final IngestaoService ingestao;
    private final IngestaoProperties props;
    private final Executor executor;

    private volatile Andamento andamento = new Andamento(Situacao.PARADO, null, null, 0, null);

    @Autowired
    public PainelDeIngestao(DescobertaDeCanal descoberta, IngestaoService ingestao, IngestaoProperties props) {
        this(descoberta, ingestao, props, Executors.newSingleThreadExecutor());
    }

    PainelDeIngestao(DescobertaDeCanal descoberta, IngestaoService ingestao, IngestaoProperties props,
                     Executor executor) {
        this.descoberta = descoberta;
        this.ingestao = ingestao;
        this.props = props;
        this.executor = executor;
    }

    public Andamento andamento() {
        return andamento;
    }

    public synchronized void iniciar(Pedido pedido) {
        if (andamento.situacao() == Situacao.RODANDO) {
            throw new EmAndamentoException();
        }
        andamento = new Andamento(Situacao.RODANDO, "Procurando os vídeos do canal…", null, 0, pedido);
        executor.execute(() -> rodar(pedido));
    }

    private void rodar(Pedido pedido) {
        try {
            DescobertaDeCanal.Resultado achados =
                    descoberta.descobrir(props.dados(), pedido.canal(), pedido.idioma(), pedido.max());
            String nomeDoArquivo = achados.arquivo().getFileName().toString();
            String canal = nomeDoArquivo.substring(0, nomeDoArquivo.length() - ".csv".length());

            RelatorioIngestao relatorio = ingestao.executar(canal, pedido.limite(), mensagem ->
                    andamento = new Andamento(Situacao.RODANDO, mensagem, null, 0, pedido));
            andamento = new Andamento(Situacao.CONCLUIDO, "Pronto.", relatorio.resumo(), relatorio.adiados(), pedido);
        } catch (IngestaoInterrompidaException e) {
            andamento = new Andamento(Situacao.ERRO, explicar(e.getCause()),
                    e.getRelatorioParcial().resumo(), 0, pedido);
        } catch (RuntimeException e) {
            log.error("Falha na ingestão pelo painel", e);
            andamento = new Andamento(Situacao.ERRO, explicar(e), null, 0, pedido);
        }
    }

    private String explicar(Throwable causa) {
        if (causa instanceof FalhaNaBuscaDeLegendaException) {
            return "O YouTube bloqueou este IP por excesso de pedidos. Espere algumas horas e clique em Continuar. "
                    + "O que já foi indexado está salvo.";
        }
        if (causa instanceof YouTubeApiException) {
            return "A API do YouTube recusou o pedido (" + causa.getMessage()
                    + "). Confira a YOUTUBE_API_KEY no .env e a cota do dia.";
        }
        if (causa instanceof IllegalArgumentException) {
            return causa.getMessage();
        }
        return "Erro inesperado: " + causa.getMessage() + ". Veja o terminal onde o painel está rodando.";
    }

    @PreDestroy
    void encerrar() {
        if (executor instanceof ExecutorService servico) {
            servico.shutdownNow();
        }
    }
}
