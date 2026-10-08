package com.lucasmanoel.ouvirfrase.repository;

import com.lucasmanoel.ouvirfrase.model.Video;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VideoRepository extends JpaRepository<Video, Long> {

    Optional<Video> findByYoutubeId(String youtubeId);
    boolean existsByYoutubeId(String youtubeId);
}
