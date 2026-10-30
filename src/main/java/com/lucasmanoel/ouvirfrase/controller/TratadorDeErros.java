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

    private static final String MENSAGEM = "Parâmetros inválidos. Informe uma frase de até 200 caracteres e um idioma válido (ex.: en).";

    // @NotBlank / @Size nos parâmetros do controller, ou parâmetro "frase" ausente
    @ExceptionHandler({HandlerMethodValidationException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ErroResposta> requisicaoInvalida(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErroResposta("requisicao_invalida", MENSAGEM));
    }
}
