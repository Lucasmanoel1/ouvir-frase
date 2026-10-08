package com.lucasmanoel.ouvirfrase.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "ocorrencia")
@Builder
public class Ocorrencia {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ocorrencia_seq")
    @SequenceGenerator(name = "ocorrencia_seq", sequenceName = "ocorrencia_seq", allocationSize = 50)
    private Long id;

    private Long videoId;
    private Long trechoId;
    private String palavra;
    private Integer posicao;
    private Double inicioSeg;
}
