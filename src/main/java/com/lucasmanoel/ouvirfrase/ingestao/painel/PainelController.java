package com.lucasmanoel.ouvirfrase.ingestao.painel;

import com.lucasmanoel.ouvirfrase.dto.ErroResposta;
import com.lucasmanoel.ouvirfrase.ingestao.painel.PainelDeIngestao.Andamento;
import com.lucasmanoel.ouvirfrase.ingestao.painel.PainelDeIngestao.Pedido;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

/** API do painel de ingestão. Só existe com o profile "painel" (uso local). */
@RestController
@RequestMapping("/api/painel/ingestao")
@Profile("painel")
@RequiredArgsConstructor
public class PainelController {

    private static final Pattern IDIOMA = Pattern.compile("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*");

    private final PainelDeIngestao painel;

    /** Campos opcionais chegam nulos e ganham o padrão. */
    public record NovaIngestao(String canal, String idioma, Integer max, Integer limite) {
    }

    @GetMapping
    public Andamento andamento() {
        return painel.andamento();
    }

    // consumes JSON: um site de fora não consegue enviar isso sem passar por CORS, que não está liberado
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> iniciar(@RequestBody NovaIngestao corpo) {
        String canal = corpo.canal() == null ? "" : corpo.canal().trim();
        String idioma = corpo.idioma() == null ? "en" : corpo.idioma().trim();
        int max = corpo.max() == null ? 100 : corpo.max();
        int limite = corpo.limite() == null ? 15 : corpo.limite();

        if (canal.isEmpty() || canal.length() > 200) {
            return invalido("Informe o link ou o @ do canal (até 200 caracteres).");
        }
        if (!IDIOMA.matcher(idioma).matches()) {
            return invalido("Idioma inválido. Use um código como en, es ou pt.");
        }
        if (max < 1 || max > 500) {
            return invalido("Vídeos a descobrir: de 1 a 500.");
        }
        if (limite < 1 || limite > 50) {
            return invalido("Vídeos por lote: de 1 a 50.");
        }

        try {
            painel.iniciar(new Pedido(canal, idioma, max, limite));
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
