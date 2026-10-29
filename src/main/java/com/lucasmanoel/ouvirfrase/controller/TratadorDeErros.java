package com.lucasmanoel.ouvirfrase.controller;

import com.lucasmanoel.ouvirfrase.dto.ErroResposta;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class TratadorDeErros {

    private static final String MENSAGEM_FRASE = "Informe uma frase de até 200 caracteres.";

    // @NotBlank / @Size nos parâmetros do controller
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErroResposta> validacao(HandlerMethodValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErroResposta("requisicao_invalida", MENSAGEM_FRASE));
    }

    // parâmetro "frase" ausente
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErroResposta> parametroAusente(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErroResposta("requisicao_invalida", MENSAGEM_FRASE));
    }
}
