package com.lucasmanoel.ouvirfrase.repository;

import com.lucasmanoel.ouvirfrase.ingestao.indexacao.IndexadorDeVideo;
import com.lucasmanoel.ouvirfrase.ingestao.legenda.TrechoLegenda;
import com.lucasmanoel.ouvirfrase.service.BuscaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Repository;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Confirma qual exceção chega ao código quando o Postgres cancela uma consulta por statement_timeout.
 * É ela que o TratadorDeErros transforma em 503.
 */
@SpringBootTest(properties = "spring.datasource.hikari.connection-init-sql=SET statement_timeout = '300ms'")
@Import(TimeoutDoBancoTest.Config.class)
@Testcontainers
class TimeoutDoBancoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired ConsultaLenta consultaLenta;
    @Autowired BuscaService buscaService;
    @Autowired IndexadorDeVideo indexador;

    @Test
    void deveTraduzirOTimeoutDoPostgresParaQueryTimeoutException() {
        assertThatThrownBy(consultaLenta::dormir).isInstanceOf(QueryTimeoutException.class);
    }

    @Test
    void deveContinuarBuscandoDepoisDoTimeout() {
        indexador.indexar("tmo001", "Video", "en", List.of(new TrechoLegenda("I really love this idea", 10.0, 4.0)));

        assertThatThrownBy(consultaLenta::dormir).isInstanceOf(QueryTimeoutException.class);

        assertThat(buscaService.buscar("love this idea", "en").resultados()).hasSize(1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean
        ConsultaLenta consultaLenta() {
            return new ConsultaLenta();
        }
    }

    /** @Repository para o proxy traduzir a exceção como faz no BuscaRepository. */
    @Repository
    static class ConsultaLenta {
        @PersistenceContext EntityManager em;

        void dormir() {
            em.createNativeQuery("SELECT pg_sleep(1)").getSingleResult();
        }
    }
}
