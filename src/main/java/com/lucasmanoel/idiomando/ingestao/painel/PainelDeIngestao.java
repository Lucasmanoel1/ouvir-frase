package com.lucasmanoel.idiomando.ingestao.painel;

import com.lucasmanoel.idiomando.ingestao.IngestaoInterrompidaException;
import com.lucasmanoel.idiomando.ingestao.IngestaoProperties;
import com.lucasmanoel.idiomando.ingestao.IngestaoService;
import com.lucasmanoel.idiomando.ingestao.RelatorioIngestao;
import com.lucasmanoel.idiomando.ingestao.catalogo.ListaDeVideos;
import com.lucasmanoel.idiomando.ingestao.legenda.FalhaNaBuscaDeLegendaException;
import com.lucasmanoel.idiomando.ingestao.youtube.DescobertaDeCanal;
import com.lucasmanoel.idiomando.ingestao.youtube.YouTubeApiException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Roda a ingestão em segundo plano, uma por vez, e guarda o andamento para a página consultar.
 * Link colado = descobrir os vídeos do canal ou da playlist (grava o CSV) + indexar um lote dessa lista.
 * Lista já cadastrada = só indexar um lote (sem falar com o YouTube para descobrir).
 * Só existe com o profile "painel" (uso local; nunca vai para produção).
 */
@Service
@Profile("painel")
@Slf4j
public class PainelDeIngestao {

    public enum Situacao { PARADO, RODANDO, CONCLUIDO, ERRO }

    /**
     * O que o Lucas pediu na tela.
     *
     * @param link  link do canal ou da playlist; vem nulo quando só se indexa uma lista cadastrada
     * @param lista nome do CSV da lista cadastrada (na pasta do idioma); nulo quando veio um link
     */
    public record Pedido(String link, String idioma, String lista, int max, int limite) {

        public boolean soIndexar() {
            return lista != null;
        }
    }

    /**
     * @param mensagem frase curta para a tela (etapa em curso, resultado ou o que deu errado)
     * @param relatorio texto do relatório da ingestão, quando houver
     * @param adiados  vídeos aprovados que ficaram para o próximo lote (> 0 = mostrar "Continuar")
     * @param lista    a lista que está sendo (ou foi) indexada; nula enquanto não se sabe qual é
     */
    public record Andamento(Situacao situacao, String mensagem, String relatorio, int adiados,
                            Pedido pedido, ListaDeVideos lista) {
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

    private volatile Andamento andamento = new Andamento(Situacao.PARADO, null, null, 0, null, null);

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
        String mensagem = pedido.soIndexar() ? "Preparando o lote…" : "Procurando os vídeos…";
        andamento = new Andamento(Situacao.RODANDO, mensagem, null, 0, pedido, null);
        executor.execute(() -> rodar(pedido));
    }

    private void rodar(Pedido pedido) {
        ListaDeVideos lista = null;
        try {
            if (pedido.soIndexar()) {
                lista = new ListaDeVideos(pedido.idioma(), pedido.lista());
            } else {
                DescobertaDeCanal.Resultado achados =
                        descoberta.descobrir(props.dados(), pedido.link(), pedido.idioma(), pedido.max());
                lista = achados.lista();
                if (achados.gravados() == 0 && Files.notExists(achados.arquivo())) {
                    // nada novo e nenhum CSV criado: não há lista para indexar
                    andamento = new Andamento(Situacao.CONCLUIDO, "Nenhum vídeo novo encontrado nesse link.",
                            null, 0, pedido, null);
                    return;
                }
            }

            // guarda a lista já aqui: se o YouTube bloquear depois, a tela ainda sabe qual era
            ListaDeVideos alvo = lista;
            andamento = new Andamento(Situacao.RODANDO, andamento.mensagem(), null, 0, pedido, alvo);
            RelatorioIngestao relatorio = ingestao.executar(alvo, pedido.limite(), mensagem ->
                    andamento = new Andamento(Situacao.RODANDO, mensagem, null, 0, pedido, alvo));
            andamento = new Andamento(Situacao.CONCLUIDO, "Pronto.", relatorio.resumo(), relatorio.adiados(),
                    pedido, alvo);
        } catch (IngestaoInterrompidaException e) {
            andamento = new Andamento(Situacao.ERRO, explicar(e.getCause()),
                    e.getRelatorioParcial().resumo(), 0, pedido, lista);
        } catch (RuntimeException e) {
            log.error("Falha na ingestão pelo painel", e);
            andamento = new Andamento(Situacao.ERRO, explicar(e), null, 0, pedido, lista);
        }
    }

    private String explicar(Throwable causa) {
        if (causa instanceof FalhaNaBuscaDeLegendaException) {
            return "O YouTube bloqueou este IP por excesso de pedidos. Espere algumas horas e clique em Tentar de novo. "
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
