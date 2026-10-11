package com.lucasmanoel.ouvirfrase.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.within;

public class EstimadorDeTempoTest {

    private final EstimadorDeTempo estimador = new EstimadorDeTempo();

    @Test
    void primeiraPalavraComecaNoInicioDoTrecho(){
        assertThat(estimador.estimarInicio(10.0, 5.0, 0, 5))
                .isCloseTo(10.0, within(0.001));
    }

    @Test
    void deveRejeitarTotalDePalavrasZero() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> estimador.estimarInicio(10.0, 5.0, 0, 0));
    }
}
