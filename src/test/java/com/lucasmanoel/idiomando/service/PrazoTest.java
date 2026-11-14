package com.lucasmanoel.idiomando.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PrazoTest {

    @Test
    void deveTerTempoSobrandoLogoDepoisDeCriado() {
        Prazo prazo = Prazo.de(Duration.ofSeconds(10));

        assertThat(prazo.esgotado()).isFalse();
        assertThat(prazo.restanteMs()).isBetween(1L, 10_000L);
    }

    @Test
    void deveEstarEsgotadoQuandoOOrcamentoEZero() {
        Prazo prazo = Prazo.de(Duration.ZERO);

        assertThat(prazo.esgotado()).isTrue();
        assertThat(prazo.restanteMs()).isZero(); // 0 só quando acabou: no Postgres, 0 desligaria o limite
    }

    @Test
    void deveEsgotarQuandoOTempoPassa() throws InterruptedException {
        Prazo prazo = Prazo.de(Duration.ofMillis(20));

        Thread.sleep(60);

        assertThat(prazo.esgotado()).isTrue();
    }

    @Test
    void deveEsgotarQuandoOBancoCancelaUmaConsulta() {
        Prazo prazo = Prazo.de(Duration.ofSeconds(10));

        prazo.estourar();

        assertThat(prazo.esgotado()).isTrue();
    }
}
