package com.lucasmanoel.ouvirfrase.ingestao.indexacao;

import com.lucasmanoel.ouvirfrase.ingestao.legenda.TrechoLegenda;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlinhadorDeTraducaoTest {

    @Test
    void deveCasarPorTempoMesmoQuandoUmTrechoFoiDescartadoNoIndice() {
        // o trecho de 4.0 s ("[Music]") não foi indexado, então só 3 inícios guardados
        List<Double> inicios = List.of(1.0, 6.0, 9.2);
        List<TrechoLegenda> traduzidos = List.of(
                new TrechoLegenda("olá\nvocê", 1.0, 2.0),
                new TrechoLegenda("[Música]", 4.0, 2.0),
                new TrechoLegenda("como vai", 6.1, 2.0),
                new TrechoLegenda("tchau", 9.0, 1.0));

        assertThat(AlinhadorDeTraducao.alinhar(inicios, traduzidos))
                .containsExactly("olá você", "como vai", "tchau");
    }

    @Test
    void deveDeixarNuloQuemNaoTemParDentroDaTolerancia() {
        List<String> resultado = AlinhadorDeTraducao.alinhar(
                List.of(1.0, 50.0), List.of(new TrechoLegenda("olá", 1.4, 2.0)));

        assertThat(resultado).isEqualTo(Arrays.asList("olá", null));
    }
}
