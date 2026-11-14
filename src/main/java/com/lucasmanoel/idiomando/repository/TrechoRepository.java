package com.lucasmanoel.idiomando.repository;

import com.lucasmanoel.idiomando.model.Trecho;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrechoRepository extends JpaRepository<Trecho, Long> {

    List<Trecho> findByVideoYoutubeIdOrderByOrdem(String youtubeId);
}
