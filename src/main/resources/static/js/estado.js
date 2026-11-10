// Estado da busca atual, compartilhado entre os módulos. `indisponiveis`: ids de vídeo que o YouTube recusou nesta busca.
export const busca = { frase: '', idioma: 'en', resultados: [], indice: 0, fraseTransliterada: null, indisponiveis: new Set() };
