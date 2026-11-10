package com.lucasmanoel.ouvirfrase.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Vídeo que a ingestão já avaliou e rejeitou; serve para não consultar o YouTube de novo a cada rodada. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "video_descartado")
public class VideoDescartado {

    @Id
    private String youtubeId;
    private String motivo;
    private LocalDateTime data;
}
