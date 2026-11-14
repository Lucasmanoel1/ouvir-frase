package com.lucasmanoel.idiomando.controller;

import com.lucasmanoel.idiomando.dto.RespostaBusca;
import com.lucasmanoel.idiomando.service.BuscaService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BuscaController {

    /** Código de idioma aceito (en, pt-BR, zh-CN...). Também usado pelo painel. */
    public static final String IDIOMA = "[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*";

    private final BuscaService buscaService;

    @GetMapping("/busca")
    public RespostaBusca buscar(@RequestParam @NotBlank @Size(max = 200) String frase,
                                @RequestParam(defaultValue = "en")
                                @Pattern(regexp = IDIOMA) @Size(max = 10) String idioma) {
        return buscaService.buscar(frase, idioma);
    }

    @GetMapping("/idiomas")
    public List<String> idiomas() {
        return buscaService.idiomas();
    }
}
