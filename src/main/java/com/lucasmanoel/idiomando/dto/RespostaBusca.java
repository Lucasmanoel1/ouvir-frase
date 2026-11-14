package com.lucasmanoel.idiomando.dto;

import java.util.List;

public record RespostaBusca(String idioma, List<ResultadoBusca> resultados, String fraseTransliterada) {
}
