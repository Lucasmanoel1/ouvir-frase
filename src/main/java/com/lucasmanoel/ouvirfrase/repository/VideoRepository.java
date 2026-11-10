package com.lucasmanoel.ouvirfrase.repository;

import com.lucasmanoel.ouvirfrase.model.Video;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VideoRepository extends JpaRepository<Video, Long> {

    Optional<Video> findByYoutubeId(String youtubeId);
    boolean existsByYoutubeId(String youtubeId);

    @Query("select v.idioma from Video v group by v.idioma order by count(v) desc, v.idioma")
    List<String> idiomasDisponiveis();
}
