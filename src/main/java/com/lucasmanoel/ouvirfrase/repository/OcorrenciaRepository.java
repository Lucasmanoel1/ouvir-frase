package com.lucasmanoel.ouvirfrase.repository;

import com.lucasmanoel.ouvirfrase.model.Ocorrencia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OcorrenciaRepository extends JpaRepository<Ocorrencia, Long> {
}
