package com.lucasmanoel.idiomando.repository;

import com.lucasmanoel.idiomando.model.Ocorrencia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OcorrenciaRepository extends JpaRepository<Ocorrencia, Long> {
}
