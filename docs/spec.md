# Spec: lote de melhorias de novembro/2026

> Fase 2 de 3 (Pesquisa → **Spec** → Code). Transforma o `PRD.md` v2 em arquivos e mudanças exatas. Não tem implementação: só assinaturas, contratos, classes CSS, constantes e testes.
> Base: `develop` em 02/11/2026 (merge do PR #27), lida arquivo por arquivo na pasta do projeto.
> **Antes do código:** responder as decisões da seção 1 (D1 a D7). Onde a spec depende de uma delas, está marcado `[D#]` e a opção escrita é a recomendada.

Abreviações de caminho:
- `J/` = `src/main/java/com/lucasmanoel/ouvirfrase/`
- `T/` = `src/test/java/com/lucasmanoel/ouvirfrase/`
- `S/` = `src/main/resources/static/`
- `P/` = `src/main/resources/painel/`
- `TJS/` = `src/test/js/` (só se D1 for aprovada)

---

## 1. Divergências entre o PRD e o código, e decisões pendentes

### 1.1 Decisões que são suas (escopo)

| # | Onde | O PRD diz | O que encontrei | Proposta |
|---|---|---|---|---|
| **D1** | Testes do front | "funções puras testáveis"; regras lógicas do front com boa cobertura | Não existe teste de JS no repositório; o Playwright vive só na sessão. Testei: o Node 22 importa os `.js` de `static/js/` como módulo ES sem `package.json` e sem dependência. | Usar o **`node:test`** (embutido no Node, zero dependência): arquivos `src/test/js/*.test.mjs`, rodados com `node --test "src/test/js/*.test.mjs"`, e um passo no CI (`actions/setup-node`, Node 22). O `./mvnw verify` não roda esses testes. Se recusar, os mesmos cenários viram roteiro do Playwright na sessão. |
| **D2** | R6 no celular | Risco: "Chrome no Android tem histórico de duplicar resultados" | O MDN (browser-compat-data 8.1.8) diz outra coisa: no **Chrome Android, `continuous` "pode ser definido, mas não tem efeito"** (crbug 41297427). Lá o reconhecimento continua parando na primeira pausa. | **Aceitar** e registrar no README (o R6 vale para computador). A alternativa, reiniciar o reconhecimento no `onend` até dar 3 s de silêncio, junta pedaços na mão e cria os duplicados que o PRD temia. Não recomendo para protótipo. |
| **D3** | R2, fim da fala | Interpolar dentro da fala (início + duração do trecho) | Na legenda automática, a duração de um trecho costuma invadir o seguinte (o contexto, seção 8, deixou isso em aberto: "só se o Lucas pedir"). Sem ajuste, o sublinhado anda devagar demais nessas falas. | No **karaokê** (só no front), usar `fim = min(início + duração, início da próxima fala)`. Uma linha. Não mexe no backend nem no banco. |
| **D4** | R5, `Pedido` | "`Pedido` ganha um modo" | Um enum de modo seria redundante: "só indexar" é exatamente "veio o nome da lista". E o campo `canal` passa a receber link de playlist. | `Pedido(link, idioma, lista, max, limite)` com `soIndexar() = lista != null`. Renomear `canal` para `link` no JSON e na página (muda o contrato do painel, que é só local). |
| **D5** | R5, "Tentar de novo" | Só fala do **Continuar** | Depois de um 429, o botão **Tentar de novo** repete o pedido inteiro: redescobre o canal e gasta cota, o mesmo defeito do A3. | Se o andamento já conhece a lista, **Tentar de novo** só indexa (igual ao Continuar). |
| **D6** | A5, tokenização | `comparacao.js` com `tokens` e `comparar` | O karaokê (R2) precisa das palavras **com posição no texto original** (para montar os spans e achar o marca-texto); a comparação precisa do texto normalizado. Se a tokenização ficar em `comparacao.js`, o `destaque.js` importaria de um módulo de "comparação". | Criar `S/js/palavras.js` (separar palavras com posição; CJK um por caractere) e fazer `comparacao.js` e `destaque.js` usarem ele. |
| **D7** | R8 no celular | "Firefox/Safari continuam com a lista nativa" | O **Safari 27** (14/09/2026, também no iOS) já suporta `base-select`. No Chrome Android 135+ e no iOS 27, o select deixa de abrir o seletor do sistema (roleta/folha) e abre a lista estilizada. Só o Firefox fica com a nativa. | Aceitar (é o pedido estético, e o PRD não pediu nativo no celular). Ajustar o aceite do R8: Firefox nativo, Safari 27+ igual ao Chrome. Se preferir o seletor do sistema no celular: envolver o CSS em `@media (pointer: fine)`. |

### 1.2 Ajustes sem decisão (só registro)

- **R7, consulta SQL:** o PRD sugere self-join em `ocorrencia`. Usei `lead()` (função de janela): como `posicao` não tem buracos dentro do vídeo, dá o mesmo resultado com uma leitura só da tabela. Seção 7.
- **Painel, texto do erro 429:** `PainelDeIngestao.explicar` manda "clique em Continuar", mas no estado de erro o botão se chama **Tentar de novo**. Corrigir o texto (entra na branch 1).
- **R4, handle não latino (achado novo, extensão do A2):** handles do YouTube aceitam letras de outras escritas (`@канал`). O `slug()` também devolve `canal` nesse caso, não só quando se cola o id `UC...`. A reserva tem de ser o id do canal vindo da resposta (`items[].id`), não o texto colado.
- **R5, contagens:** reaproveitar `triar` faz 1 a 2 consultas por vídeo (`existsByYoutubeId`, `existsById`). Hoje são ~1.100 vídeos nos CSVs, ou seja, até ~2.200 consultas locais por abertura do painel. Aceitável no PC; se ficar lento, troca-se por uma consulta de ids. Não fazer agora.
- **R5, total por lista:** o `CatalogoDeVideos` descarta vídeo repetido entre arquivos (fica a primeira ocorrência, em ordem de caminho). O "total" de uma lista pode ser menor que o número de linhas do CSV. É o comportamento certo; o README explica.
- **R1, BGN:** o padrão usa o primo `ʹ` para o sinal brando e `ë` para `ё` (`большое` → `bolʹshoye`, `ёлка` → `yëlka`). É o padrão, não erro. Conferir no print se Lora/Inter desenham o `ʹ` (U+02B9); senão cai na fonte do sistema.
- **Fora do lote, mas muda o README depois:** o Firefox 160 (beta; lançamento em 10/11/2026) passa a ter `SpeechRecognition`. O microfone vai acender sozinho lá (o site detecta o recurso). A frase "só Chrome e Edge" vai ficar velha.

---

## 2. Verificações na documentação oficial

### 2.1 ICU4J (R1)
- **Versão atual: 79.1**, de 22/10/2026 (página de downloads do ICU). Coordenadas: `com.ibm.icu:icu4j:79.1`. Requer Java 11+ ("works on Java 11..25"). CLDR 49. Jar de 15,3 MB (baixei o do release no GitHub).
- **Thread-safety:** o javadoc do `Transliterator` (ICU4J 79) diz: *"Transliterator objects are stateless; they retain no information between calls to transliterate(). As a result, threads may share transliterators without synchronizing them."* A ressalva é só sobre trocar o filtro com o objeto em uso, o que não faremos. **Pode compartilhar a instância. Não precisa de `ThreadLocal`.**
- **Teste prático (79.1):** 8 threads × 20.000 chamadas na mesma instância de `Russian-Latin/BGN`: 0 divergências em relação ao resultado sequencial.
- **Achado:** o mesmo javadoc diz que `getInstance()` com o mesmo id devolve o mesmo objeto, mas na 79.1 devolveu objetos diferentes (`==` deu `false`). Por isso: criar uma vez e guardar em campo, não chamar `getInstance` por requisição.
- **IDs:** `Russian-Latin/BGN` existe (alias de `ru-ru_Latn/BGN` no `root.txt` das transformações do ICU). Saídas reais na 79.1:
  - `Russian-Latin/BGN`: `Привет` → `Privet`; `Привет, как дела?` → `Privet, kak dela?`; `Спасибо большое` → `Spasibo bolʹshoye`; `Щука, ёлка, йогурт, мать, ещё` → `Shchuka, yëlka, yogurt, matʹ, yeshchë`.
  - `Any-Latin`: `안녕하세요` → `annyeonghaseyo`; `你好` → `nǐ hǎo`; `こんにちは` → `kon'nichiha`; `日本語` → `rì běn yǔ` (confirma a limitação do PRD: kanji sai em pinyin); `hello` → `hello`.

### 2.2 `<select>` customizável (R8)
Fonte: `web-features` 3.45.0 e `@mdn/browser-compat-data` 8.1.8 (os dados por trás do webstatus.dev), publicados em 02/11/2026.
- **Baseline: não.**
- `appearance: base-select`, `::picker(select)`, `<selectedcontent>`: Chrome, Edge e Chrome Android **135**; Safari e Safari iOS **27**.
- `::checkmark` e `::picker-icon`: Chrome/Edge **133**; Safari **27**.
- **Firefox:** `base-select` só atrás de flag (`dom.select.customizable_select.enabled` e `layout.css.appearance-base.enabled`, desde a 149). `::picker`, `::checkmark`, `::picker-icon` e `selectedcontent` não existem lá. Resultado: lista nativa, como hoje.
- MDN: o select **e** o `::picker(select)` precisam de `appearance: base-select`; sem suporte, cai no select clássico ("progressive enhancement"); o picker vai para a top layer e se posiciona por `anchor()` em relação ao select (âncora implícita); `option::checkmark` vai para o fim da linha com `order: 1; margin-left: auto`. Chrome: com `base-select` o select não abre o componente do sistema no celular.

### 2.3 Web Speech API (R6)
Fonte: especificação (W3C, draft do Audio WG) e MDN.
- `continuous = false`: no máximo **um** resultado final. `true`: zero ou mais resultados finais em reconhecimentos seguidos.
- `interimResults = false`: nenhum resultado parcial. Não afeta os finais.
- `SpeechRecognitionEvent.results`: *"array of all current recognition results for this session"* (os finais até agora + os parciais atuais). `resultIndex`: menor índice que mudou.
- `stop()`: pede ao serviço o resultado do áudio **já captado** (vem um resultado final ou `nomatch`). `abort()`: não devolve nada.
- `end` dispara sempre que a sessão acaba, por qualquer motivo.
- `no-speech`: a especificação **não define tempo**; depende do navegador. Daí o teto próprio de espera inicial.
- Suporte (BCD 8.1.8): Chrome/Edge (sem prefixo desde a 139); Safari 14.1+ com prefixo `webkit` e `continuous` correto desde o 17; **Chrome Android: `continuous` sem efeito** (ver D2); Firefox 160 (beta).

### 2.4 Outros
- `node:test` (D1): testado com Node 22.23: `node --test "src/test/js/*.test.mjs"` importa `S/js/*.js` sem `package.json` e sem aviso.
- Phosphor: `@phosphor-icons/core` 2.1.1 tem `assets/bold/arrows-clockwise-bold.svg` (usado no R7).
- YouTube Data API: `playlists.list` custa 1 unidade de cota, como `channels.list`.

---

## 3. Branch 1: `feature/painel-listas` (R5, R4, A1 a A4)

### 3.1 Arquivos

| Ação | Caminho |
|---|---|
| criar | `J/ingestao/catalogo/ListaDeVideos.java` |
| criar | `J/ingestao/ResumoLista.java` |
| criar | `T/ingestao/catalogo/ListaDeVideosTest.java` |
| criar | `T/ingestao/youtube/YouTubeCanalClientTest.java` |
| modificar | `J/ingestao/catalogo/VideoSemente.java`, `J/ingestao/catalogo/CatalogoDeVideos.java` |
| modificar | `J/ingestao/IngestaoService.java` |
| modificar | `J/ingestao/youtube/YouTubeCanalClient.java`, `J/ingestao/youtube/DescobertaDeCanal.java` |
| modificar | `J/ingestao/painel/PainelDeIngestao.java`, `J/ingestao/painel/PainelController.java` |
| modificar | `P/index.html`, `P/painel.css`, `P/painel.js` |
| modificar | `T/ingestao/IngestaoServiceTest.java`, `T/ingestao/youtube/DescobertaDeCanalTest.java`, `T/ingestao/painel/PainelDeIngestaoTest.java`, `T/ingestao/painel/PainelControllerTest.java` |
| modificar | `README.md` |
| sem mudança | `IngestaoRunner` (o `--canal` passa a aceitar link de playlist sozinho, porque a descoberta é que decide), `CatalogoDeVideos.idsConhecidos`, `listarVideos` |

### 3.2 O que muda em cada arquivo

**`ListaDeVideos` (novo, `ingestao/catalogo/`).** Uma lista = um CSV em `<dados>/<idioma>/<nome>.csv`. Concentra a regra "idioma vem da pasta, nome vem do arquivo", hoje repetida no `CatalogoDeVideos` (linhas do `substring`) e no `PainelDeIngestao.rodar`.
```java
public record ListaDeVideos(String idioma, String nome) {
    static final String PREFIXO_PLAYLIST = "playlist-";
    public static ListaDeVideos doArquivo(Path csv);   // pasta pai = idioma; arquivo sem ".csv" = nome
    public boolean playlist();                          // nome começa com "playlist-"
}
```

**`VideoSemente`.** Ganha `public ListaDeVideos lista()` → `new ListaDeVideos(idioma, canal)`.

**`CatalogoDeVideos.carregar`.** Troca a extração manual de idioma e canal por `ListaDeVideos.doArquivo(arquivo)`. Mesmo comportamento.

**`ResumoLista` (novo, `ingestao/`).** O que o painel mostra por lista.
```java
public record ResumoLista(String idioma, String nome, boolean playlist,
                          int total, int indexados, int descartados, int pendentes) { }
```

**`IngestaoService`.**
- `executar(String canal, int limite, Consumer<String>)` vira `executar(ListaDeVideos lista, int limite, Consumer<String> progresso)`. Filtro: `lista == null || lista.equals(s.lista())`. **Isso resolve o A4**: compara idioma **e** nome.
- Se `lista != null` e o filtro não achar nenhuma semente: `IllegalArgumentException("Lista não encontrada: " + idioma + "/" + nome)`.
- `executar()` (linha de comando) passa `null`.
- Novo `public List<ResumoLista> listas()`: carrega o catálogo **uma vez**, agrupa por `VideoSemente::lista` num `LinkedHashMap` (mantém a ordem dos caminhos) e chama o `triar` existente em cada grupo. `total` = sementes da lista; `indexados` = `jaIndexados`; `descartados` = `jaDescartados`; `pendentes` = `novas.size()`. Não chama filtro, legenda nem YouTube.
- `triar` e `Triagem` continuam privados.

**`YouTubeCanalClient`.**
- O record `Canal(titulo, uploadsPlaylistId, slug)` vira **`Fonte(String titulo, String playlistId, String nomeArquivo)`**. Os nomes das classes (`YouTubeCanalClient`, `DescobertaDeCanal`) ficam, como o PRD pediu ("generalizar sem renomear tudo").
- Novo `public Fonte resolverFonte(String entrada)`: se a entrada tiver `list=`, `resolverPlaylist`; senão, `resolverCanal`.
- `resolverCanal` passa a ser privado e devolve `Fonte`. `nomeArquivo = slug(handle != null ? handle : titulo, item.id())` (**A2**: a reserva é o id do canal da resposta). `ItemCanal` ganha o campo `String id`.
- Novo `private Fonte resolverPlaylist(String playlistId)`:
  - Recusa **antes de chamar a API** (`IllegalArgumentException`): id começando com `RD` (mix automático) ou igual a `WL`, `LL`, `LM` (Assistir mais tarde, Gostei, músicas curtidas). Mensagem: "Mixes automáticos e listas pessoais (Assistir mais tarde, Gostei) não podem ser lidos pela API. Use uma playlist pública."
  - `GET /playlists?part=snippet&id=<id>` (1 unidade).
  - Sem `items`: "Playlist não encontrada ou privada: <entrada>".
  - `nomeArquivo = "playlist-" + slug(titulo, playlistId)`.
- Constante nova: `Pattern LISTA = Pattern.compile("[?&]list=([\\w-]+)")`. Aceita `.../playlist?list=PL...` e `watch?v=...&list=PL...`.
- `slug(String texto)` vira **`static String slug(String texto, String reserva)`**: mesma normalização; se der vazio, normaliza a reserva.
- Records de resposta novos: `RespostaPlaylists(List<Playlist> items)` e `Playlist(Snippet snippet)`. O `SnippetCanal(String title)` atual é renomeado para `Snippet(String title)` e serve aos dois (não duplicar).
- Javadoc da classe: incluir `playlists.list` na lista de endpoints de 1 unidade.

**`DescobertaDeCanal`.**
- `descobrir(...)` chama `client.resolverFonte(entrada)`; arquivo = `raiz/idioma/fonte.nomeArquivo() + ".csv"`; `listarVideos(fonte.playlistId(), ...)`. Log: "Fonte '{}' -> {}".
- `Resultado(Path arquivo, int gravados)` ganha o método `public ListaDeVideos lista()` → `ListaDeVideos.doArquivo(arquivo)`.

**`PainelDeIngestao`** `[D4]` `[D5]`.
```java
public record Pedido(String link, String idioma, String lista, int max, int limite) {
    public boolean soIndexar() { return lista != null; }
}
public record Andamento(Situacao situacao, String mensagem, String relatorio, int adiados,
                        Pedido pedido, ListaDeVideos lista) { }   // lista = a que está sendo/foi indexada
```
- `iniciar`: mensagem inicial "Procurando os vídeos…" ou, se `soIndexar()`, "Preparando o lote…".
- `rodar`: `lista = soIndexar ? new ListaDeVideos(idioma, pedido.lista()) : descoberta.descobrir(...).lista()`. Logo depois de conhecer a lista, grava o andamento **com a lista** (um 429 que vier depois ainda sabe qual era). Depois, `ingestao.executar(lista, limite, progresso)`. O progresso e o fim também levam a lista. Some o `substring` do nome do arquivo.
- `explicar`: o texto do 429 passa a dizer "clique em Tentar de novo".

**`PainelController`.**
- `@RequestMapping("/api/painel")`; `@GetMapping("/ingestao")`, `@PostMapping(path = "/ingestao", consumes = JSON)` e o novo **`@GetMapping("/listas")`**.
- Injeta também o `IngestaoService` (para `listas()`).
- `NovaIngestao(String link, String idioma, String lista, Integer max, Integer limite)`.
- Validação (400 com `ErroResposta("requisicao_invalida", ...)`):
  - com `lista`: precisa casar `[A-Za-z0-9_-]{1,120}` ("Lista inválida."); `link` e `max` são ignorados;
  - sem `lista`: `link` de 1 a 200 caracteres ("Informe o link do canal ou da playlist (até 200 caracteres).") e `max` de 1 a 500;
  - `idioma` e `limite` como hoje.
- `GET /api/painel/listas` → `200` com `List<ResumoLista>`. Se o catálogo falhar (`IllegalArgumentException` de idioma que não bate com a pasta, `UncheckedIOException`): `500` com `ErroResposta("listas_ilegiveis", mensagem)`.

Contratos JSON:
```
POST /api/painel/ingestao   {"link":"https://www.youtube.com/playlist?list=PL…","idioma":"es","max":100,"limite":15}
POST /api/painel/ingestao   {"idioma":"ru","lista":"qwrtru","limite":15}        ← só indexar
GET  /api/painel/ingestao → {"situacao":"CONCLUIDO","mensagem":"Pronto.","relatorio":"…","adiados":12,
                             "pedido":{"link":null,"idioma":"ru","lista":"qwrtru","max":100,"limite":15},
                             "lista":{"idioma":"ru","nome":"qwrtru"}}
GET  /api/painel/listas   → [{"idioma":"en","nome":"ted","playlist":false,"total":208,"indexados":43,
                              "descartados":2,"pendentes":163}, …]
```

**`P/index.html`.**
- Título "Ingerir um canal ou uma playlist". Subtítulo "Cole o link do canal ou da playlist. O painel acha os vídeos, confere as legendas e indexa um lote."
- Campo `id="canal"` vira `id="link"`; `aria-label="Link do canal ou da playlist"`; placeholder `https://www.youtube.com/@TEDTalks`. Botão: "Ingerir".
- **A1:** `<option value="zh">` vira `<option value="zh-CN">`. (`pt` fica: não há pasta, mas não atrapalha.)
- Ajuda de "Vídeos a descobrir": "Quantos vídeos novos buscar no canal ou na playlist (1 a 500)."
- Nova seção depois de `#andamento`:
  `<section id="cadastradas" class="card cadastradas" aria-labelledby="cadastradas-titulo">` com `<h2 id="cadastradas-titulo">Listas cadastradas</h2>`, `<ul id="cadastradas-itens" class="cadastradas-itens"></ul>` e `<p id="cadastradas-vazio" class="dica" hidden>Nenhuma lista em data/. Cole um link acima para criar a primeira.</p>`.

**`P/painel.css`.** Classes novas `.cadastradas`, `.cadastradas-itens`, `.cadastrada` (grade: informações à esquerda, botão à direita; uma coluna abaixo de 560 px), `.cadastrada-nome`, `.cadastrada-numeros` (`--suave`, `tabular-nums`). O tipo (Canal/Playlist) e o idioma reaproveitam a pílula `.selo` do `style.css`.
**Não usar `.lista*`:** o painel carrega o `style.css`, que já tem `.lista`, `.lista-topo` e `.lista-titulo` (com `overflow: hidden` e `flex`).

**`P/painel.js`.**
- `campos` usa `$('link')`; o envio do formulário manda `{ link, idioma, max, limite }`.
- Novas: `const API_LISTAS = '/api/painel/listas'`, `carregarListas()`, `mostrarListas(listas)`, `ingerirLista(l)` → `iniciar({ idioma: l.idioma, lista: l.nome, limite: Number($('limite').value) })`.
- Cada linha: nome, `.selo` com "Canal"/"Playlist" e o idioma, a linha "N de T indexados · D descartados · P pendentes" e o botão **Ingerir lote** (só com `pendentes > 0`).
- `mostrar(a)`: desabilita os botões das listas enquanto `RODANDO`. Textos: "Lote concluído" / "Lista concluída"; "Faltam N vídeos desta lista…".
- **Continuar** → `iniciar({ idioma: a.lista.idioma, lista: a.lista.nome, limite: a.pedido.limite })`.
- **Tentar de novo** `[D5]` → igual ao Continuar quando `a.lista` existe; senão `iniciar(a.pedido)`.
- `preencher(pedido)`: só preenche o link quando `pedido.link` vier.
- `atualizar()`: quando a situação **não** for `RODANDO`, chama `carregarListas()`. A consulta periódica para quando a tarefa acaba, então isso roda uma vez por fim de lote e uma vez ao abrir a página.

**`README.md`.** Seção do painel: playlist ("cole o link do canal ou da playlist"; mixes e listas pessoais recusados), "Listas cadastradas" e "Ingerir lote", Continuar sem redescobrir, nome `playlist-<slug>.csv`. Linha de comando: `--canal` aceita link de playlist. Explicar que o total de uma lista não conta vídeo repetido em outro CSV.

### 3.3 Testes

| Classe | Cenários |
|---|---|
| `ListaDeVideosTest` (novo) | `doArquivo` tira idioma da pasta e nome do arquivo (`data/zh-CN/playlist-abc.csv` → `zh-CN`, `playlist-abc`, `playlist()` verdadeiro); nome sem prefixo → `playlist()` falso. |
| `YouTubeCanalClientTest` (novo, `MockRestServiceServer` como o `YouTubeDataClientTest`) | 1) link de playlist (`…/playlist?list=PLabc`) chama `/playlists` com `part=snippet` e `id=PLabc` e devolve `Fonte(título, "PLabc", "playlist-<slug>")`; 2) `watch?v=…&list=PLabc` também é playlist; 3) playlist com título sem letra ASCII → `playlist-plabc` (A2); 4) canal por handle → nome = slug do handle (regressão); 5) canal pelo id `UC…` com título cirílico → nome = slug do id (A2); 6) `list=RD…`, `WL`, `LL` são recusados **sem requisição** (`@ParameterizedTest`; `servidor.verify()` sem expectativas); 7) playlist sem `items` → `IllegalArgumentException`. |
| `IngestaoServiceTest` (ajustar) | O teste `deveIndexarSoOCanalPedido…` passa a usar `ListaDeVideos` e ganha um vídeo `es/ted` com o **mesmo nome** em outra pasta, que não pode ser tocado (A4). Novo `deveResumirAsListasSemChamarOYouTube`: duas listas (uma com indexado, descartado e pendente; outra só com pendente), confere `ResumoLista` de cada uma e `verifyNoInteractions(filtro, legendaProvider, indexador)`. Novo `deveRecusarListaQueNaoExiste`. |
| `DescobertaDeCanalTest` (ajustar) | Troca `Canal` por `Fonte` e `resolverCanal` por `resolverFonte`; acrescenta a verificação de `r.lista()` = `en/novo`. |
| `PainelDeIngestaoTest` (ajustar) | Pedidos com `link`; `executar(eq(new ListaDeVideos("en","tedtalks")), …)`. Novos: `deveSoIndexarSemDescobrirQuandoVemALista` (`verifyNoInteractions(descoberta)`; andamento final com a lista); `deveGuardarAListaMesmoQuandoOYouTubeBloqueia` (429 depois da descoberta: `ERRO`, `lista` preenchida, mensagem com "Tentar de novo"). |
| `PainelControllerTest` (ajustar) | `@MockitoBean IngestaoService`. Campo `link` nos testes atuais. Novos: `lista` vira pedido de só indexar (link e max ignorados); `lista` inválida (`../x`) → 400; sem link e sem lista → 400; `GET /api/painel/listas` devolve o JSON de `ResumoLista`; catálogo com erro → 500 com `erro = listas_ilegiveis`. |
| Página do painel (Playwright, sessão) | Abrir lista as 6 listas com contagens; clicar em Ingerir lote numa lista russa; Continuar manda `lista` (conferir o corpo do POST); botões desabilitados enquanto roda; link de playlist preenche o formulário ao recarregar. |

### 3.4 Critérios de aceite (do PRD)
- Colar um link de playlist com vídeos de canais diferentes cria `data/<idioma>/playlist-<slug>.csv`, indexa um lote e mostra **Continuar** se sobrar.
- Mix (`list=RD…`) e "Assistir mais tarde" são recusados com mensagem clara.
- Abrir o painel lista `en/ted`, `en/teded`, `es/tededespanol` e os três de `ru/` com total, indexados, descartados e pendentes.
- **Ingerir lote** numa lista russa indexa sem chamar a descoberta.
- **Continuar** não chama `channels.list` (nem `playlistItems.list`).
- Dois canais russos colados pelo id `UC…` não caem no mesmo `canal.csv` (A2).
- Chinês pelo painel grava em `data/zh-CN/` (A1).
- Mesmo nome de arquivo em duas pastas de idioma não se mistura (A4).

### 3.5 Commits
Cada commit compila: o ajuste de teste existente vai junto com a mudança de assinatura que o quebra. Classes de teste novas vão em `test:`.
1. `refactor: centraliza idioma e nome da lista em ListaDeVideos`: `ListaDeVideos`, `VideoSemente`, `CatalogoDeVideos`.
2. `feat: ingestao filtra por idioma e lista e resume as listas`: `IngestaoService`, `ResumoLista`, ajuste em `IngestaoServiceTest` e `PainelDeIngestao` (só a chamada nova de `executar`) e `PainelDeIngestaoTest`.
3. `feat: descoberta aceita playlist e usa o id como reserva do nome`: `YouTubeCanalClient`, `DescobertaDeCanal`, `DescobertaDeCanalTest`.
4. `feat: painel indexa listas cadastradas sem redescobrir`: `PainelDeIngestao`, `PainelController`, `PainelDeIngestaoTest`, `PainelControllerTest`.
5. `feat: pagina do painel mostra as listas cadastradas e aceita playlist`: `P/index.html`, `P/painel.css`, `P/painel.js`.
6. `test: cobre ListaDeVideos, playlist e listas do painel`: `ListaDeVideosTest`, `YouTubeCanalClientTest`, os testes novos de `IngestaoServiceTest`.
7. `docs: painel com playlist e listas cadastradas no README`: `README.md`.

### 3.6 Os 5 erros
- **Overengineering:** sem enum de modo `[D4]`, sem coluna nova, sem migração; o tipo da lista sai do prefixo do nome.
- **Repetir código:** `triar` reaproveitado nas contagens; `listarVideos` sem mudança; `ListaDeVideos` acaba com a extração duplicada de nome; um `Snippet` para canal e playlist; `.selo` reaproveitado.
- **Não saber fazer:** endpoints e custo conferidos na documentação da YouTube Data API.
- **Reinventar a roda:** nenhum parser de URL próprio além de uma regex de `list=`.
- **Juntar tudo:** contagem no serviço de ingestão, decisão de modo no painel, resolução de fonte no client.

---

## 4. Branch 2: `feature/pratica-fala` (R6, R3, R1, A5 parte)

### 4.1 Arquivos

| Ação | Caminho |
|---|---|
| modificar | `pom.xml` (dependência `com.ibm.icu:icu4j:79.1`) |
| criar | `J/service/Escritas.java`, `J/service/Transliterador.java` |
| modificar | `J/dto/RespostaBusca.java`, `J/dto/ResultadoBusca.java`, `J/service/BuscaService.java` |
| criar | `S/js/palavras.js` `[D6]`, `S/js/comparacao.js` |
| modificar | `S/js/pronuncia.js`, `S/js/api.js`, `S/js/app.js`, `S/js/estado.js`, `S/index.html`, `S/style.css` |
| criar | `T/service/EscritasTest.java`, `T/service/TransliteradorTest.java` |
| modificar | `T/controller/BuscaControllerTest.java`, `T/service/BuscaServiceTest.java` |
| criar `[D1]` | `TJS/palavras.test.mjs`, `TJS/comparacao.test.mjs` |
| modificar `[D1]` | `.github/workflows/ci.yml` |
| modificar | `README.md` |

### 4.2 O que muda em cada arquivo

**`pom.xml`.** `com.ibm.icu:icu4j` versão `79.1` (scope compile).

**`Escritas` (novo, `service/`).** Classe utilitária (final, construtor privado), sem Spring. Base do R1 e, na branch 3, do R9.
```java
public static Set<Character.UnicodeScript> de(String texto);
// EnumSet das escritas das LETRAS do texto (Character.isLetter), ignorando COMMON e INHERITED.
// null ou sem letras → conjunto vazio.
```

**`Transliterador` (novo, `service/`, `@Component`).**
```java
public String transliterar(String texto, String idioma);
// null quando o texto é null/vazio ou quando Escritas.de(texto) está vazio ou só tem LATIN.
// "ru" → Russian-Latin/BGN; qualquer outro idioma → Any-Latin.
```
- As duas instâncias (`Transliterator.getInstance("Russian-Latin/BGN")` e `("Any-Latin")`) são criadas **uma vez**, no construtor, e guardadas em campos `final`. Compartilhadas entre threads (seção 2.1). Sem `ThreadLocal`, sem cache próprio.
- Só o pacote `service` importa `com.ibm.icu`.

**`ResultadoBusca`.** Novo componente no fim: `String legendaTransliterada`.

**`RespostaBusca`.** `RespostaBusca(List<ResultadoBusca> resultados, String fraseTransliterada)`.

**`BuscaService`.**
- Injeta `Transliterador`.
- `exatos(palavras, idioma)`: preenche `legendaTransliterada = transliterador.transliterar(a.legenda(), idioma)`.
- `montarResultado(c, videoId, v)` ganha o parâmetro `idioma` e faz o mesmo.
- `buscar` devolve `new RespostaBusca(resultados, transliterador.transliterar(frase, idioma))`. A frase é transliterada **como foi digitada**, não os tokens. Frase sem tokens: `new RespostaBusca(List.of(), null)`.

Contrato de `GET /api/busca` nesta branch:
```json
{ "resultados": [ { "youtubeId": "…", "titulo": "…", "inicioSeg": 9.0, "fimSeg": 15.0,
                    "legenda": "Привет, друзья", "pontuacao": 1.0, "exata": true,
                    "legendaAutomatica": false, "legendaTransliterada": "Privet, druzʹya" } ],
  "fraseTransliterada": "privet" }
```

**`S/js/palavras.js` (novo, puro, sem DOM)** `[D6]`.
```js
export function palavras(texto)   // → [{ texto, inicio, fim }] sobre o texto ORIGINAL
// palavra = [\p{L}\p{M}\p{N}]+ com apóstrofo (' ou ’) só entre caracteres;
// Han, Hiragana e Katakana: um item por caractere (mesma regra do Normalizador).
```

**`S/js/comparacao.js` (novo, puro).** Sai de `pronuncia.js` tudo o que hoje está sob "comparação (pura, sem DOM)".
```js
export function tokens(texto)                 // NFKC, ’‘ → ', minúsculas, tira [..] e (..), depois palavras(...)
export function comparar(esperado, ouvido)    // → { pontuacao, casadas, palavras: [{ texto, ok }], ouvido: string[] }
                                              //   (ganha o campo "casadas"; o resto igual a hoje)
export function deveMostrarOuvido(c)          // R3: c.pontuacao < 1 || c.ouvido.length > c.casadas
export function alternativasDe(resultados)    // R6; resultados = [{ final: boolean, alternativas: string[] }]
// sem finais → []; um final → as alternativas dele; vários → [finais.map(f => f.alternativas[0]).join(' ')]
```
`chave()` (sem acento latino e sem apóstrofo) fica privada aqui.

**`S/js/pronuncia.js`.** Fica só com microfone e tela.
- Constantes: `SILENCIO_MS = 3000`, `ESPERA_INICIAL_MS = 8000`, `TETO_MS = 30000`.
- Estado: `relogios = { silencio: 0, teto: 0 }`, `pararRelogios()`; `limpar()` também para os relógios.
- `ouvir()`: `continuous = true`, `interimResults = true`, `maxAlternatives = 3`. Ao iniciar, arma o silêncio com `ESPERA_INICIAL_MS` e o teto com `TETO_MS`; os dois chamam `minha.stop()` (não `abort()`: o `stop()` entrega o resultado do áudio já captado).
- `onresult`: rearma o silêncio com `SILENCIO_MS` (inclusive nos parciais) e guarda uma **cópia** de `ev.results` no formato `{ final: r.isFinal, alternativas: [...r].map(a => a.transcript) }`.
- O resultado deixa de ser mostrado no `onresult` e passa para o `onend`: para os relógios, volta o botão e, se `alternativasDe(copia)` não estiver vazio, chama `mostrarResultado`; senão, a mensagem de hoje ("Não ouvi nada…") quando não houve erro.
- Clicar em **Parar** continua chamando `rec.stop()`.
- `mostrarResultado`: `$('mic-ouvido').hidden = !deveMostrarOuvido(melhor)` (**R3**).
- **R1:** nova `mostrarAlvo()`, chamada em `limpar()` (que já roda a cada troca de resultado e de aba Frase/Trecho). Lê `config.getAlvo(alvo)` → `{ texto, latino }`; `#pratica-alvo` aparece só com `latino`; preenche `#alvo-original` e `#alvo-latino`.
- `config.getAlvo(tipo)` passa a devolver `{ texto, latino }`; `ouvir()` usa `.texto`.
- Exporta só `{ iniciar, limpar }` (`tokens` e `comparar` saem dos exports; ninguém mais os importa).

**`S/index.html`.** No `.pratica`, depois de `.pratica-topo`:
```html
<p id="pratica-alvo" class="pratica-alvo" hidden>
  <span id="alvo-original" dir="auto"></span>
  <span id="alvo-latino" class="alvo-latino"></span>
</p>
```
Opcional (o PRD deixou opcional; só com seu ok): `title="Mostra as letras, não a pronúncia exata"` no `#alvo-latino`.

**`S/style.css`.** `.pratica-alvo` (`margin-top: 10px`, grade de uma coluna, `gap: 2px`); `#alvo-original` (Lora 500, ~1.05rem); `.alvo-latino` (`--suave`, .9rem). Os dois com até 3 linhas (`-webkit-line-clamp: 3`), porque o Trecho pode ser longo.

**`S/js/estado.js`.** `busca` ganha `fraseTransliterada: null`.

**`S/js/api.js`.** `buscarFrases` devolve `{ resultados: dados.resultados || [], fraseTransliterada: dados.fraseTransliterada ?? null }`.

**`S/js/app.js`.** Guarda `busca.fraseTransliterada`. O `getAlvo` do `Pronuncia.iniciar`:
`frase` → `{ texto: busca.frase, latino: busca.fraseTransliterada }`; `trecho` → `{ texto: r.legenda, latino: r.legendaTransliterada }`.

**`.github/workflows/ci.yml`** `[D1]`. Antes do Maven: `actions/setup-node@v4` com `node-version: '22'` e o passo `node --test "src/test/js/*.test.mjs"`.

**`README.md`.** Contrato da API com os dois campos novos; Front: transliteração no cartão de prática, microfone espera ~3 s de silêncio; Limitações: transliteração indica as letras, não a pronúncia; japonês com kanji sai em pinyin (avaliar o Kuromoji quando indexar japonês); Chrome Android encerra na primeira pausa `[D2]`; testes do front `[D1]`.

### 4.3 Testes

| Classe | Cenários |
|---|---|
| `EscritasTest` (novo) | Um `@ParameterizedTest`: `hello world` → `{LATIN}`; `Привет, 2024!` → `{CYRILLIC}`; `日本語です` → `{HAN, HIRAGANA}`; `123 ?!` → vazio; `null` → vazio. |
| `TransliteradorTest` (novo, `new Transliterador()`) | 1) russo BGN: `Привет` → `Privet` e `Спасибо большое` → `Spasibo bolʹshoye` (documenta o padrão); 2) texto latino (`hello`, `café`) → `null`; 3) `null`, vazio e só pontuação → `null`; 4) idioma sem regra própria usa `Any-Latin` (`ko`, `안녕하세요` → `annyeonghaseyo`); 5) misto em russo `Hello, Привет` → `Hello, Privet`. |
| `BuscaControllerTest` (ajustar) | O teste de contrato passa a conferir `legendaTransliterada` e `fraseTransliterada`. |
| `BuscaServiceTest` (ajustar) | Novo `deveTransliterarAFraseEALegendaEmRusso` (indexa um vídeo `ru`, busca `привет`, confere os dois campos). No teste inglês existente, acrescentar a verificação de que os dois vêm `null` (sem teste novo). |
| `TJS/palavras.test.mjs` `[D1]` | Posições no texto original com pontuação; apóstrofo reto e curvo dentro da palavra; apóstrofo solto não entra; CJK um por caractere com posições. |
| `TJS/comparacao.test.mjs` `[D1]` | `tokens`: tira `[Music]`, normaliza `’`; `comparar`: ignora acento e apóstrofo, conta `casadas`, respeita a ordem; `deveMostrarOuvido`: 100% exato → falso, 100% com palavra a mais → verdadeiro, 60% → verdadeiro; `alternativasDe`: sem finais → `[]`, um final → as 3 alternativas, dois finais → uma string juntando a melhor de cada, parciais ignorados. |
| Playwright (sessão, voz simulada) | R6: dois resultados finais com 1,5 s entre eles → uma tentativa só com a frase inteira; 3 s sem `onresult` → `stop()` e resultado; clicar em Parar → encerra na hora; nada em 8 s → "Não ouvi nada". R1: `привет` em `ru` mostra as duas linhas; Trecho mostra a do trecho; `hello` em `en` não mostra a linha. R3 na tela. |

### 4.4 Critérios de aceite (do PRD)
- Falar "thank you … (pausa de 1,5 s) … very much" → uma tentativa só, com a frase inteira (Chrome e Edge de computador).
- Ficar 3 s calado depois de falar → encerra e mostra o resultado.
- Clicar em Parar → encerra na hora.
- 100% exato → sem "Entendi"; 100% com palavra extra → aparece; 60% → aparece.
- Buscar `привет` em russo → o cartão mostra `Привет` e `Privet`; trocar para **Trecho** mostra a transliteração do trecho; buscar `hello` em inglês → nenhuma linha extra.

### 4.5 Commits
1. `chore: adiciona icu4j 79.1`: `pom.xml`.
2. `feat: busca devolve a transliteracao da frase e da legenda`: `Escritas`, `Transliterador`, `RespostaBusca`, `ResultadoBusca`, `BuscaService`, ajuste em `BuscaControllerTest`.
3. `test: cobre Escritas, Transliterador e a busca em russo`: `EscritasTest`, `TransliteradorTest`, `BuscaServiceTest`.
4. `refactor: separa palavras e comparacao da tela do microfone`: `palavras.js`, `comparacao.js`, `pronuncia.js` (só mover, sem mudar comportamento).
5. `feat: microfone espera 3 s de silencio e Entendi so quando acrescenta`: `comparacao.js`, `pronuncia.js`.
6. `feat: cartao de pratica mostra a transliteracao`: `index.html`, `style.css`, `pronuncia.js`, `api.js`, `app.js`, `estado.js`.
7. `test: testes do front com node:test` `[D1]`: `TJS/*.test.mjs`.
8. `ci: roda os testes do front` `[D1]`: `ci.yml`.
9. `docs: transliteracao, microfone e limitacoes no README`: `README.md`.

### 4.6 Os 5 erros
- **Overengineering:** sem `ThreadLocal` (a documentação permite compartilhar); sem reinício automático no Android `[D2]`; o R3 é uma condição.
- **Repetir código:** `Escritas` nasce aqui e é reaproveitada no R9; `palavras.js` acaba com a regex repetida (completa na branch 4); o `limpar()` existente vira o gatilho do R1.
- **Não saber fazer:** ICU, Web Speech e suporte conferidos (seção 2).
- **Reinventar a roda:** ICU em vez de tabela de letras; `stop()` da própria API para pegar o último pedaço.
- **Juntar tudo:** comparação pura separada do microfone; transliteração numa classe própria, fora do `BuscaService`.

---

## 5. Branch 3: `feature/idioma-auto` (R9, R8, R7)

Antes desta branch: ingerir `es` e `ru` pelo painel e rodar a consulta da seção 7 para escolher os exemplos.

### 5.1 Arquivos

| Ação | Caminho |
|---|---|
| modificar | `J/service/Escritas.java`, `J/service/BuscaService.java`, `J/dto/RespostaBusca.java` |
| criar | `S/js/exemplos.js` |
| modificar | `S/js/api.js`, `S/js/app.js`, `S/js/idiomas.js`, `S/js/historico.js`, `S/js/util.js`, `S/style.css`, `S/icons.svg` |
| modificar | `T/service/EscritasTest.java`, `T/service/BuscaPorIdiomaTest.java`, `T/controller/BuscaControllerTest.java` |
| criar `[D1]` | `TJS/util.test.mjs` |
| modificar | `README.md` |
| sem mudança | `P/*` (o painel usa a mesma pílula `.idioma` e herda o R8 pelo `style.css`) |

### 5.2 O que muda em cada arquivo

**`Escritas`.** Ganha:
```java
private static final Map<String, Set<Character.UnicodeScript>> POR_IDIOMA;
// en, es, fr, de, it, pt → LATIN; ru → CYRILLIC; ko → HANGUL; ja → HIRAGANA, KATAKANA, HAN; zh-CN → HAN
public static boolean compativel(String idioma, Set<Character.UnicodeScript> escritas);
// escritas vazias (só número/pontuação) → true; idioma fora do mapa → true; senão POR_IDIOMA.get(idioma).containsAll(escritas)
```

**`RespostaBusca`.** `RespostaBusca(String idioma, List<ResultadoBusca> resultados, String fraseTransliterada)`. `idioma` = o idioma em que a busca foi feita de fato.

**`BuscaService`** (mesma assinatura pública `buscar(frase, idioma)`).
- O corpo atual de `buscar` vira `private List<ResultadoBusca> buscarEm(String frase, String idioma)` (tokeniza com o idioma, exatos e depois aproximados, como hoje).
- `private RespostaBusca resposta(String frase, String idioma, List<ResultadoBusca> resultados)` monta a resposta com `fraseTransliterada` **do idioma usado**.
- Algoritmo:
  1. `escritas = Escritas.de(frase)`.
  2. Se `compativel(escolhido, escritas)`: `buscarEm(frase, escolhido)`; se não vier vazio, devolve com `escolhido`. (Quando a frase existe nos dois idiomas, vale o escolhido.)
  3. `outros = videoRepository.idiomasDisponiveis()` (já ordenados pelo tamanho do índice) menos o escolhido, filtrados por `compativel`.
  4. Para cada um, na ordem: se `exatos(tokens no idioma, idioma)` não vier vazio, devolve `buscarEm(frase, idioma)` com esse idioma.
  5. Último recurso: para cada um, na ordem, o primeiro `buscarEm` não vazio (só pode ser aproximado).
  6. Nada: devolve vazio com o escolhido.
- Custo extra só quando o passo 2 vem vazio ou a escrita não bate.
- Os tokens são refeitos por idioma (o `Normalizador` trata o apóstrofo do inglês diferente).

Contrato final de `GET /api/busca`:
```json
{ "idioma": "es", "resultados": [ … ], "fraseTransliterada": null }
```

**`S/js/api.js`.** `buscarFrases` também devolve `idioma: dados.idioma`.

**`S/js/idiomas.js`** (dono do seletor e da pílula).
```js
export function aplicarIdiomaDetectado(codigo)  // busca.idioma, idiomaSel.value (só se existir a opção) e data-trocou na pílula
export function limparSinal()                   // tira data-trocou
```
Em `iniciarIdiomas`, `idiomaSel` ganha listeners de `focus` e `change` que chamam `limparSinal()`.

**`S/js/app.js`.**
- A linha do `history.replaceState` vira `atualizarUrl()`, chamada no começo da busca e de novo se o idioma mudar.
- No começo de `buscar()`: `limparSinal()` (o sinal some na próxima interação).
- Depois da resposta, se `dados.idioma` for diferente de `busca.idioma`: `aplicarIdiomaDetectado(dados.idioma)`, `atualizarUrl()` e `prepararTraducao(dados.idioma, !!navigator.userActivation?.isActive)` (o modelo de tradução certo começa a baixar). Isso vem **antes** de `guardarHistorico`, para o recente ficar no idioma certo.
- **Sem mensagem**, como decidido.

**`S/style.css`, sinal (R9).**
```css
.idioma[data-trocou]::after  /* ponto laranja de 12 px no canto superior direito da pílula, traço 2 px; pulsa 2 vezes */
@keyframes ponto             /* escala 1 → 1,35 → 1 */
```
A regra global de `prefers-reduced-motion` já desliga a animação: fica só o ponto.

**`S/style.css`, seletor (R8)** `[D7]`. Tudo dentro de `@supports (appearance: base-select) { … }`. Fora do bloco, nada muda: o Firefox continua igual a hoje.
- `.idioma select, .idioma select::picker(select) { appearance: base-select; }`
- `.idioma select`: zera o que o `base-select` põe por padrão (borda, fundo, padding), para continuar parecendo texto dentro da pílula.
- `.idioma select::picker-icon { display: none; }`: a seta continua sendo o `svg.caret` que já existe na pílula (funciona com e sem suporte).
- `.idioma select::picker(select)`: `background: var(--papel-2)`, `border: var(--traco)`, `border-radius: var(--raio)`, `box-shadow: var(--sombra)`, `padding: 6px`, `min-width: 160px`, espaço abaixo da pílula (~14 px). Alinhar pela borda direita quando a pílula está no canto da tela inicial, para não sair da tela. Acertar com print em 1366 px e 390 px.
- `.idioma option`: igual a `.popover button` (`flex`, 36 px de altura, raio 8, peso 600, .92rem); `:hover` e `:focus-visible` em `--laranja-claro`.
- `.idioma option:checked { background: var(--laranja-claro); }`.
- `.idioma option::checkmark`: `order: 1; margin-left: auto; content: ""`, 1em × 1em, `background: currentColor` com `mask` do símbolo `i-check` do Phosphor em data-URI (o caminho do sprite). Sem glifo de texto, por causa da regra "só Phosphor".
- **Zero JavaScript.**

**`S/icons.svg`.** Novo `<symbol id="i-arrows-clockwise" viewBox="0 0 256 256">` com o path de `@phosphor-icons/core/assets/bold/arrows-clockwise-bold.svg`.

**`S/js/exemplos.js` (novo, só dados).**
```js
export const EXEMPLOS = { en: [/* ≥ 20 */], es: [/* ≥ 20 */], ru: [/* ≥ 20 */] };
```
As frases vêm da consulta da seção 7, escolhidas por você. O chat de código **não inventa** frases. Só entram idiomas indexados de fato.

**`S/js/util.js`.** Nova função pura:
```js
export function fatiaCircular(lista, inicio, n)  // até min(n, lista.length) itens a partir de inicio % length, dando a volta
```

**`S/js/historico.js`.**
- Importa `EXEMPLOS` de `exemplos.js` e `fatiaCircular` de `util.js`. Some o `EXEMPLOS` local.
- `const POR_VEZ = 3`; `const paginas = new Map()` (idioma → página; a primeira é sorteada para não mostrar sempre as mesmas 3).
- `renderChips()`:
  - exemplos = lista do idioma, menos os que já estão nos recentes do idioma (regra de hoje); mostra `fatiaCircular(disponiveis, pagina * POR_VEZ, POR_VEZ)`; se houver mais de 3, acrescenta o botão de troca (`chip-btn chip-trocar`, ícone `arrows-clockwise`, `aria-label` e `title` "Outros exemplos") que avança a página e redesenha. Andar de 3 em 3 cobre a lista inteira antes de repetir.
  - recentes: só os de `x.l === busca.idioma`. O clique só preenche e busca: **sai** a troca de idioma ao clicar num recente (e o `idiomaSel` deixa de ser usado neste arquivo).
- Idioma sem lista: a seção some (como hoje).

**`S/style.css`, R7.** `.chip-trocar` (34 × 34, ícone centralizado, mesma borda do `.chip-btn`).

**`README.md`.** API com `idioma`; Front: detecção do idioma (escrita + índice; vale o escolhido quando a frase existe nos dois), exemplos por idioma, recentes por idioma; Limitações: seletor estilizado no Chrome/Edge 135+ e Safari 27+, nativo no Firefox.

### 5.3 Testes

| Classe | Cenários |
|---|---|
| `EscritasTest` (ajustar) | Novo `@ParameterizedTest` de `compativel`: `es`+`{LATIN}` → verdadeiro; `ru`+`{LATIN}` → falso; `ja`+`{HAN, HIRAGANA}` → verdadeiro; `zh-CN`+`{HAN, HIRAGANA}` → falso; `ar` (fora do mapa) → verdadeiro; qualquer idioma + vazio → verdadeiro. |
| `BuscaPorIdiomaTest` (ajustar; já tem `en` e `es` e o Testcontainers) | Acrescentar vídeos: `es` "muchas gracias amigo", `ru` "привет друзья", e "no" em `en` e `es`. Testes: 1) o atual (idiomas passam a incluir `ru`); 2) `muchas gracias` com `en` → `idioma = es` e resultados do vídeo espanhol; 3) `привет` com `en` → `ru`; 4) `no` com `en` fica `en` e com `es` fica `es`; 5) frase que não existe → `idioma` escolhido e lista vazia; 6) último recurso: `muchas gracias mi amigo` com `en` → `es`, `exata = false`. |
| `BuscaControllerTest` (ajustar) | Contrato com `idioma`. |
| `TJS/util.test.mjs` `[D1]` | `fatiaCircular`: começo da lista; dá a volta no fim; lista menor que `n` devolve a lista sem repetir; lista vazia → `[]`. |
| Playwright (sessão) | R9: seletor em `en`, buscar `muchas gracias` → seletor `es`, `l=es` na URL, ponto na pílula, nenhuma mensagem; o ponto some ao focar o seletor ou na próxima busca; recente gravado em `es`. R7: 3 exemplos, o botão troca, mudar o idioma troca exemplos e recentes. R8: prints da lista aberta no Chrome (início e estúdio, desktop e 390 px) e do painel. |

### 5.4 Critérios de aceite (do PRD)
- Seletor em inglês, buscar `muchas gracias` → resultados em espanhol, seletor muda e a pílula mostra o sinal, sem mensagem.
- Buscar `привет` com o seletor em inglês → vai direto ao russo.
- Buscar `no` em inglês → fica no inglês.
- No Chrome/Edge (e Safari 27+) a lista aberta tem o visual do projeto: cartão, traço, sombra dura, opção marcada em laranja-claro com check. No Firefox continua a lista nativa. O painel herda o mesmo visual. `[D7]`
- Inglês mostra 3 de ≥ 20; o botão troca; mudar para espanhol troca exemplos e recentes; nenhum exemplo devolve vazio (conferir cada um no site antes do commit).

### 5.5 Commits
1. `feat: busca detecta o idioma da frase pela escrita e pelo indice`: `Escritas`, `BuscaService`, `RespostaBusca`, ajuste em `BuscaControllerTest`.
2. `test: cobre a deteccao de idioma`: `EscritasTest`, `BuscaPorIdiomaTest`.
3. `feat: front aplica o idioma detectado com sinal na pilula`: `api.js`, `app.js`, `idiomas.js`, `style.css` (sinal).
4. `feat: seletor de idioma com o visual do projeto`: `style.css` (bloco `@supports`).
5. `feat: exemplos por idioma com troca e recentes do idioma atual`: `exemplos.js`, `historico.js`, `util.js`, `icons.svg`, `style.css` (`.chip-trocar`).
6. `test: cobre a fatia circular dos exemplos` `[D1]`: `TJS/util.test.mjs`.
7. `docs: deteccao de idioma, seletor e exemplos no README`: `README.md`.

### 5.6 Os 5 erros
- **Overengineering:** sem biblioteca de detecção (o índice decide); seletor só com CSS; troca de exemplos por página em vez de embaralhar.
- **Repetir código:** `Escritas` da branch 2; `idiomasDisponiveis()` já ordena; o visual do picker copia as regras de `.popover`; `exatos` e `buscarEm` reaproveitados.
- **Não saber fazer:** suporte do `base-select` conferido em dados oficiais.
- **Reinventar a roda:** `appearance: base-select` em vez de menu próprio; `Character.UnicodeScript` em vez de faixas de Unicode à mão.
- **Juntar tudo:** dados dos exemplos em `exemplos.js`, lógica em `historico.js`; seletor e pílula ficam com `idiomas.js`.

---

## 6. Branch 4: `feature/legenda-karaoke` (R2, A5 parte)

### 6.1 Arquivos

| Ação | Caminho |
|---|---|
| modificar | `S/js/palavras.js`, `S/js/destaque.js`, `S/js/legenda.js`, `S/style.css` |
| modificar `[D1]` | `TJS/palavras.test.mjs` |
| modificar | `README.md` |

### 6.2 O que muda em cada arquivo

**`S/js/palavras.js`.** Ganha:
```js
export function indiceFalado(pesos, fracao)
// pesos = caracteres de cada palavra (CJK = 1); fracao = (t - início) / duração.
// fracao ≤ 0 → 0; ≥ 1 → última; sem palavras → -1; senão a palavra cujo trecho acumulado de pesos contém a fração.
```

**`S/js/destaque.js`** (completa o A5).
- `renderLegenda(alvo, texto, frase, aceitaSoltas = true)` passa a **devolver** `HTMLElement[]`: um `<span class="w">` por palavra de `palavras(texto)`, na ordem. Os separadores (espaço, pontuação) ficam como nós de texto entre os spans.
- O marca-texto continua igual para o usuário:
  - frase exata: um `<mark>` envolve os spans (e os separadores entre eles) do trecho casado;
  - palavras soltas (aproximada): um `<mark>` por span casado.
- As palavras da frase saem de `palavras(frase)`, não mais da regex local. **Cuidado:** hoje as palavras são juntadas com o separador `[^\p{L}\p{M}\p{N}]+` (um ou mais). Entre dois itens CJK, que agora vêm um por caractere, o separador tem de aceitar **zero** caracteres, senão `你好` nunca casa.
- Some a regex de palavra local; fica só `escaparRegex`.
- O `estudio.js` não muda: ele ignora o retorno.

**`S/js/legenda.js`.**
- Estado novo: `spans = []`, `pesos = []`, `palavraAtual = -1`.
- `aoTempo(t)`:
  - quando a fala muda (o `if` de hoje): `spans = renderLegenda(...)`, `pesos = spans.map(s => [...s.textContent].length)`, `palavraAtual = -1` e a tradução, como hoje;
  - **sempre** que houver fala: `marcarPalavra(indiceFalado(pesos, (t - inicio) / (fim - inicio)))`.
- `fim` `[D3]`: `min(cue.inicioSeg + cue.duracaoSeg, próxima.inicioSeg)`, numa função `fimDaFala(i)`.
- `marcarPalavra(k)`: se `k === palavraAtual`, não faz nada; senão tira `.falando` do span antigo e põe no novo. Nada é redesenhado a cada 100 ms.
- Antes de a legenda completa chegar (`cues === null`), `aoTempo` sai cedo como hoje: o cartão mostra o trecho estático sem sublinhado.
- Pausar congela e arrastar a barra atualiza sozinho: o relógio de 100 ms do `ferramentas.js` já chama `aoTempo` com o vídeo pausado.

**`S/style.css`.**
```css
#legenda .w.falando { text-decoration: underline 2px var(--tinta); text-underline-offset: .2em; }
```
Só no `#legenda` (a lista de ocorrências também recebe spans, mas nunca fica com `.falando`). `text-decoration` não muda o tamanho da linha: não pisca.

**`README.md`.** Front: sublinhado acompanha a palavra falada. Limitações: o tempo de cada palavra é estimado pelo número de letras dentro da fala; deriva em falas com pausa no meio; marcações como "(Laughter)" também recebem o sublinhado.

### 6.3 Testes

| Onde | Cenários |
|---|---|
| `TJS/palavras.test.mjs` `[D1]` | `indiceFalado`: com pesos `[1, 10]`, a fração 0,2 já está na 2ª palavra (palavra longa ocupa mais tempo); antes do início → 0; depois do fim → última; sem palavras → -1. |
| Playwright (sessão, player simulado) | O sublinhado avança palavra a palavra; com o vídeo pausado não muda; mudar o tempo (seek) atualiza; o `<mark>` continua na frase com o sublinhado por cima; legenda manual e automática; antes de a legenda completa carregar não há erro no console; frase aproximada marca as palavras soltas só dentro do trecho do resultado. |

### 6.4 Critérios de aceite (do PRD)
- Durante a reprodução, o sublinhado avança palavra a palavra dentro da fala.
- Não pisca.
- Funciona com legenda manual e automática.
- No estado inicial da busca (antes de a legenda completa carregar) não quebra nada.
- O marca-texto laranja na metade de baixo continua.

### 6.5 Commits
1. `refactor: destaque usa palavras.js e gera um span por palavra`: `destaque.js`, `palavras.js` (se precisar).
2. `feat: sublinhado acompanha a palavra falada`: `palavras.js` (`indiceFalado`), `legenda.js`, `style.css`.
3. `test: cobre o indice da palavra falada` `[D1]`: `TJS/palavras.test.mjs`.
4. `docs: karaoke e suas limitacoes no README`: `README.md`.

### 6.6 Os 5 erros
- **Overengineering:** interpolação linear por letras; nada de alinhamento forçado nem de tempo por palavra real (fora do lote).
- **Repetir código:** `palavras.js` serve à comparação, ao destaque e ao karaokê; o relógio do `ferramentas.js` é reaproveitado.
- **Não saber fazer:** sem API nova; o limite da biblioteca de legendas já está documentado.
- **Reinventar a roda:** `text-decoration` em vez de desenhar o traço.
- **Juntar tudo:** cálculo puro em `palavras.js`, DOM em `destaque.js`, tempo em `legenda.js`.

---

## 7. Consulta SQL do R7 (sequências de 2 e 3 palavras por idioma)

Rodar no `psql` (`docker compose exec db psql -U postgres -d ouvirfrase`). Primeiro, quais idiomas estão indexados:
```sql
SELECT idioma, count(*) AS videos FROM video GROUP BY idioma ORDER BY videos DESC;
```

Depois, para cada idioma (troque `'es'` e rode):
```sql
\set idioma 'es'

WITH seq AS (
    SELECT o.video_id,
           o.palavra                 AS p1,
           lead(o.palavra, 1) OVER w AS p2,
           lead(o.palavra, 2) OVER w AS p3
    FROM ocorrencia o
    JOIN video v ON v.id = o.video_id
    WHERE v.idioma = :'idioma'
    WINDOW w AS (PARTITION BY o.video_id ORDER BY o.posicao)
),
frases AS (
    SELECT p1 || ' ' || p2         AS frase, 2 AS palavras, video_id FROM seq WHERE p2 IS NOT NULL
    UNION ALL
    SELECT p1 || ' ' || p2 || ' ' || p3, 3,  video_id FROM seq WHERE p3 IS NOT NULL
)
SELECT frase, palavras, count(*) AS vezes, count(DISTINCT video_id) AS videos
FROM frases
GROUP BY frase, palavras
HAVING count(DISTINCT video_id) >= 3
ORDER BY videos DESC, vezes DESC
LIMIT 300;
```
- `lead()` pega a palavra seguinte do mesmo vídeo. Como `posicao` conta o vídeo inteiro sem buracos, "seguinte" é exatamente `posicao + 1`, a mesma regra da busca exata. Atravessa trechos, como a busca.
- `videos >= 3` garante que o exemplo dá vários resultados de vídeos diferentes, não um vídeo só.
- O topo vai ser dominado por combinações vazias (`of the`, `de la`). Escolha as que soam naturais sozinhas (`thank you very much`, `muchas gracias`), misturando 2 e 3 palavras.
- Para só legenda manual (exemplos mais limpos), acrescente `AND NOT v.legenda_automatica` no `WHERE`.
- As palavras saem normalizadas (minúsculas, sem pontuação), que é como o `Normalizador` trata o que o usuário digita. Então toda frase da lista dá resultado exato no idioma.
- Em inglês a consulta lê a tabela inteira do idioma e pode levar alguns segundos. É uma vez só.

---

## 8. Ordem de trabalho
1. Responder D1 a D7.
2. Branch 1 → merge → ingerir `es` e `ru` pelo painel (lotes de 15, pausa 6 s, horas entre lotes se vier 429) → `VACUUM ANALYZE ocorrencia;`.
3. Branch 2.
4. Rodar a consulta da seção 7 para `en`, `es` e `ru`; escolher ≥ 20 por idioma.
5. Branch 3 (com as frases em `exemplos.js`).
6. Branch 4.

---

## 9. Fontes
- ICU 79 (download, versão, Java 11+): https://unicode-org.github.io/icu/download/79.html e https://unicode-org.github.io/icu/download/
- Javadoc `Transliterator` (ICU4J 79), thread-safety: https://unicode-org.github.io/icu-docs/apidoc/released/icu4j/com/ibm/icu/text/Transliterator.html
- Guia de transformações do ICU: https://unicode-org.github.io/icu/userguide/transforms/general/
- Lista de IDs (`Russian-Latin/BGN`): https://github.com/unicode-org/icu/blob/main/icu4c/source/data/translit/root.txt
- Customizable select (Chrome): https://developer.chrome.com/blog/a-customizable-select
- MDN, customizable select: https://developer.mozilla.org/en-US/docs/Learn_web_development/Extensions/Forms/Customizable_select
- Estado do recurso (webstatus.dev / web-features): https://webstatus.dev/features/customizable-select e https://github.com/web-platform-dx/web-features/blob/main/features/customizable-select.yml (dados do pacote `web-features` 3.45.0 e `@mdn/browser-compat-data` 8.1.8)
- Web Speech API (especificação): https://webaudio.github.io/web-speech-api/
- MDN, `SpeechRecognition.continuous`: https://developer.mozilla.org/en-US/docs/Web/API/SpeechRecognition/continuous
- Phosphor Icons (`@phosphor-icons/core` 2.1.1): https://phosphoricons.com
