package com.lucasmanoel.ouvirfrase.model;

import jakarta.persistence.*;
import lombok.*;

import static jakarta.persistence.FetchType.LAZY;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "trecho")
@Builder
public class Trecho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "video_id")
    private Video video;

    private Integer ordem;
    private String texto;
    private Double inicioSeg;
    private Double duracaoSeg;
}
