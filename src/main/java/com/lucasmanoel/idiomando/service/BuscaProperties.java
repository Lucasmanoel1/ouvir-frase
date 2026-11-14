package com.lucasmanoel.idiomando.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Parâmetros da busca ({@code busca.*}). Os padrões ficam só aqui.
 *
 * @param orcamento tempo que uma busca inteira pode gastar nas consultas pesadas; passou disso, devolve o que
 *                  já achou (ou nada) em vez de deixar o usuário esperando
 */
@ConfigurationProperties(prefix = "busca")
public record BuscaProperties(@DefaultValue("2500ms") Duration orcamento) {
}
