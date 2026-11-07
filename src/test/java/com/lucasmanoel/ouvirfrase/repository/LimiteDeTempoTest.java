package com.lucasmanoel.ouvirfrase.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
class LimiteDeTempoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired BuscaRepository buscaRepository;
    @Autowired EntityManager em;

    @Test
    void deveCancelarNoBancoAConsultaQuePassaDoLimite() {
        buscaRepository.limitarTempo(150);

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> em.createNativeQuery("SELECT pg_sleep(3)").getSingleResult())
                .isInstanceOf(PersistenceException.class);

        // cancelada pelo banco perto dos 150 ms, não esperada até o fim
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void deveDeixarDeLimitarQuandoOValorEZero() {
        buscaRepository.limitarTempo(150);
        buscaRepository.limitarTempo(0);

        em.createNativeQuery("SELECT pg_sleep(0.4)").getSingleResult(); // não lança
    }
}
