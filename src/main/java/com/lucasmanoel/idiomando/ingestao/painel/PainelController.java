package com.lucasmanoel.idiomando.ingestao.painel;

import com.lucasmanoel.idiomando.controller.BuscaController;
import com.lucasmanoel.idiomando.dto.ErroResposta;
import com.lucasmanoel.idiomando.ingestao.IngestaoService;
import com.lucasmanoel.idiomando.ingestao.painel.PainelDeIngestao.Andamento;
import com.lucasmanoel.idiomando.ingestao.painel.PainelDeIngestao.Pedido;
import com.lucasmanoel.idiomando.ingestao.painel.sugestoes.GeradorDeSugestoes;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.UncheckedIOException;
import java.util.regex.Pattern;

/** API do painel de ingestão. Só existe com o profile "painel" (uso local). */
@RestController
@RequestMapping("/api/painel")
@Profile("painel")
@RequiredArgsConstructor
public class PainelController {

    private static final Pattern IDIOMA = Pattern.compile(BuscaController.IDIOMA);
    private static final Pattern LISTA = Pattern.compile("[A-Za-z0-9_-]{1,120}");
    private static final String IDIOMA_INVALIDO = "Idioma inválido. Use um código como en, es ou pt.";
    private static final int MAX_PADRAO = 100;

    private final PainelDeIngestao painel;
    private final IngestaoService ingestao;
    private final GeradorDeSugestoes gerador;

    /**
     * Campos opcionais chegam nulos e ganham o padrão. Com {@code lista}, só indexa essa lista
     * cadastrada e {@code link} e {@code max} são ignorados.
     */
    public record NovaIngestao(String link, String idioma, String lista, Integer max, Integer limite) {
    }

    @GetMapping("/ingestao")
    public Andamento andamento() {
        return painel.andamento();
    }

    @GetMapping("/listas")
    public ResponseEntity<?> listas() {
        try {
            return ResponseEntity.ok(ingestao.listas());
        } catch (IllegalArgumentException | UncheckedIOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErroResposta("listas_ilegiveis", e.getMessage()));
        }
    }

    /** Síncrono: o clique espera a contagem do índice inteiro (cerca de 17 s por milhão de palavras). */
    @GetMapping("/sugestoes")
    public ResponseEntity<?> sugestoes(@RequestParam(defaultValue = "en") String idioma,
                                       @RequestParam(defaultValue = "6") int minimo) {
        if (!IDIOMA.matcher(idioma).matches()) {
            return invalido(IDIOMA_INVALIDO);
        }
        if (minimo < 1 || minimo > 100) {
            return invalido("Mínimo de vídeos: de 1 a 100.");
        }
        return ResponseEntity.ok(gerador.gerar(idioma, minimo));
    }

    // consumes JSON: um site de fora não consegue enviar isso sem passar por CORS, que não está liberado
    @PostMapping(path = "/ingestao", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> iniciar(@RequestBody NovaIngestao corpo) {
        String idioma = corpo.idioma() == null ? "en" : corpo.idioma().trim();
        int limite = corpo.limite() == null ? 15 : corpo.limite();

        String link = null;
        String lista = null;
        int max = MAX_PADRAO;
        if (corpo.lista() != null) {
            lista = corpo.lista().trim();
            if (!LISTA.matcher(lista).matches()) {
                return invalido("Lista inválida.");
            }
        } else {
            link = corpo.link() == null ? "" : corpo.link().trim();
            max = corpo.max() == null ? MAX_PADRAO : corpo.max();
            if (link.isEmpty() || link.length() > 200) {
                return invalido("Informe o link do canal ou da playlist (até 200 caracteres).");
            }
            if (max < 1 || max > 500) {
                return invalido("Vídeos a descobrir: de 1 a 500.");
            }
        }
        if (!IDIOMA.matcher(idioma).matches()) {
            return invalido(IDIOMA_INVALIDO);
        }
        if (limite < 1 || limite > 50) {
            return invalido("Vídeos por lote: de 1 a 50.");
        }

        try {
            painel.iniciar(new Pedido(link, idioma, lista, max, limite));
        } catch (PainelDeIngestao.EmAndamentoException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErroResposta("em_andamento", e.getMessage()));
        }
        return ResponseEntity.accepted().body(painel.andamento());
    }

    private ResponseEntity<ErroResposta> invalido(String mensagem) {
        return ResponseEntity.badRequest().body(new ErroResposta("requisicao_invalida", mensagem));
    }
}
