package com.lucasmanoel.ouvirfrase.ingestao;

import com.lucasmanoel.ouvirfrase.dto.TrechoLegenda;

import java.util.List;

/** A legenda de um vídeo e a origem dela: manual (feita por pessoas) ou automática (reconhecimento de voz do YouTube). */
public record LegendaBaixada(List<TrechoLegenda> trechos, boolean automatica) {
}
