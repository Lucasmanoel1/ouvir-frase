package com.lucasmanoel.idiomando.controller;

import com.lucasmanoel.idiomando.dto.ErroResposta;
import com.lucasmanoel.idiomando.service.FraseLongaDemaisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    private static final String MENSAGEM = "Parâmetros inválidos. Informe uma frase de até 200 caracteres e 15 palavras e um idioma válido (ex.: en).";
    private static final String ERRO_FRASE_LONGA = "frase_longa";
    private static final String ERRO_INDISPONIVEL = "indisponivel";
    private static final String MENSAGEM_INDISPONIVEL = "O site está ocupado. Tente de novo em instantes.";

    // @NotBlank / @Size nos parâmetros do controller, ou parâmetro "frase" ausente
    @ExceptionHandler({HandlerMethodValidationException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ErroResposta> requisicaoInvalida(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErroResposta("requisicao_invalida", MENSAGEM));
    }

    // palavras demais (contadas depois da normalização, por isso não dá para validar com anotação)
    @ExceptionHandler(FraseLongaDemaisException.class)
    ResponseEntity<ErroResposta> fraseLonga(FraseLongaDemaisException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErroResposta(ERRO_FRASE_LONGA, e.getMessage()));
    }

    // consulta acima do statement_timeout, pool esgotado ou banco fora: o site responde em JSON em vez de 500
    @ExceptionHandler({QueryTimeoutException.class, CannotCreateTransactionException.class,
            DataAccessResourceFailureException.class})
    ResponseEntity<ErroResposta> bancoIndisponivel(Exception e) {
        log.warn("Banco indisponível: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErroResposta(ERRO_INDISPONIVEL, MENSAGEM_INDISPONIVEL));
    }
}
