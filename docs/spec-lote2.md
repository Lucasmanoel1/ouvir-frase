# Spec: lote 2 (recuo do trecho e gerador de exemplos)

> Fase 2 de 3 (Pesquisa → **Spec** → Code). Transforma o `PRD-lote2.md` (v1, 04/11/2026) em arquivos e mudanças exatas. Não tem implementação: só assinaturas, contratos, SQL, constantes e testes.
> Base: pasta do projeto em 04/11/2026 (branch `feature/legenda-karaoke`, com as 4 branches do lote 1 commitadas), lida arquivo por arquivo nos pontos que o PRD toca.
> **Antes do código:** responder a **D1** (seção 1.1). Onde a spec depende dela, está marcado `[D1]` e a opção escrita é a recomendada. O resto da seção 1 é só registro.

Abreviações de caminho:
- `J/` = `src/main/java/com/lucasmanoel/ouvirfrase/`
- `T/` = `src/test/java/com/lucasmanoel/ouvirfrase/`
- `S/` = `src/main/resources/static/`
- `P/` = `src/main/resources/painel/`
- `TJS/` = `src/test/js/`

---

## 1. Divergências entre o PRD e o código, e decisões

### 1.1 Decisão que é sua

| # | Onde | O PRD diz | O que encontrei | Proposta |
|---|---|---|---|---|
| **D1** | R1, filtro de canal | `canais >= 2` (ou o total do idioma, se só tiver um) para matar bordão de canal. Medido com o Brown, 15 categorias fazendo papel de canal. | 1) `video.canal` **não é o canal do YouTube**: é o nome da lista (o CSV). Hoje `en` tem 2 (`ted`, `teded`), `es` tem 2 (`tededespanol`, `playlist-tedx-talks-en-espa-ol`) e `ru` tem 3. Com 2 listas, `canais >= 2` quer dizer "aparece no TED **e** no TED-Ed". 2) A coluna veio na V2 (29/10) **sem preencher os vídeos antigos**: quem foi indexado antes tem `canal = NULL`, e `count(DISTINCT canal)` ignora `NULL`. Se todo o `ted` estiver `NULL`, o idioma conta 1 canal e toda frase que só aparece no TED some da lista. Não consegui consultar o seu banco. Medi no Brown: com 2 grupos no lugar de 15, o topo 150 muda em 11 frases (`per cent`, `los angeles`, `last night`, `as soon as`...); com metade de um grupo `NULL`, muda 1. | Manter `canais >= 2` e tratar `NULL` como um grupo só ("sem canal") com `coalesce(v.canal, '')` na consulta e no total do idioma. Sem migração. **Antes:** rode `SELECT idioma, canal, count(*) FROM video GROUP BY 1, 2 ORDER BY 1, 2;` e me diga se aparece `NULL`. Se aparecer, a proposta vale; se quiser o canal "certo", a alternativa é um `UPDATE` seu, à mão, pelos ids de cada CSV (fora do código). |

### 1.2 Ajustes sem decisão (só registro)

- **R2, nomes dos campos.** `inicioSeg` fica com o nome e muda de sentido (agora é o início estimado da primeira palavra, sem recuo). `fimSeg` vira `falaFimSeg` (mesmo valor, como o PRD pediu). Novos: `ultimaPalavraSeg`, `falaInicioSeg`. O único consumidor é o nosso front, então não há versão nem campo antigo mantido.
- **R2, busca aproximada.** O PRD manda tirar o início da fala "da mesma consulta de `fimDoTrecho`, devolvendo os dois limites". Só que o início vem do trecho da **primeira** palavra casada e o fim do trecho da **última**: são trechos diferentes. E a busca aproximada já faz outra consulta nesses mesmos trechos (`legenda`). Proposta: juntar `legenda(...)` e `fimDoTrecho(...)` numa consulta só, `falas(...)`, que devolve texto, início e fim (seção 3.2). Uma ida ao banco a menos por resultado.
- **R2, fim na busca aproximada.** `ultimaPalavraSeg` é o início da **última palavra casada**, não da última palavra da frase (essa pode ser a que faltou). O loop termina 1,5 s depois dela. É o que faz sentido para quem ouve.
- **R2, palavras soltas (correção visível).** A regra de hoje em `legenda.js` (`c.inicioSeg >= r.inicioSeg + 3 − 0,1`) exige que a fala **comece** depois da primeira palavra. Então a fala que contém a primeira palavra só é marcada quando a frase abre a fala; e perto do começo do vídeo, o `Math.max(0, …)` do backend faz a conta errar. Com o início e o fim da fala na resposta, a regra passa a ser "a fala começa dentro de `[falaInicioSeg, falaFimSeg)`": a primeira fala do resultado passa a ser marcada. É o que a regra queria desde o começo.
- **R2, player.** "Criar o player sem `start` e chamar `loadVideoById` no `onReady`": se o player for criado com `videoId`, o vídeo carrega duas vezes. Então ele é criado **sem `videoId`** e só o `loadVideoById` toca. Risco: o primeiro `loadVideoById` acontece fora do clique (no `onReady`). Hoje o primeiro vídeo já toca fora do clique (`autoplay: 1` depois de a API carregar) e funciona, porque o iframe do YouTube vem com `allow="autoplay"` e a página já teve interação. Conferir no uso; se o Chrome bloquear, o vídeo fica parado no ponto certo e o play da dock resolve.
- **R1, palavras soltas na consulta.** O PRD diz "as palavras soltas vêm todas (são poucas)". Não precisa de exceção: toda subfrase de uma frase que passou no `HAVING` está em pelo menos os mesmos vídeos, então também passa. Um `HAVING` só para tudo, mesmo resultado.
- **R1, `videos >= mínimo` no Java.** O `HAVING` da consulta já corta. O ranking não repete o filtro.
- **R1, quantas aparecem.** O backend devolve as 150 primeiras; o front tira as que já estão em `exemplos.js` e mostra o resto (150 menos as puladas). O aceite diz "até 150". Não vale uma constante a mais para completar 150.
- **R1, chinês e japonês.** O `Normalizador` já decide por escrita (Han, Hiragana, Katakana: um caractere por token). Ele ganha `juntar(tokens)`, que só põe espaço quando um dos dois vizinhos não é caractere solto. A regra fica num lugar só.
- **R1, onde fica o código da página.** A parte pura (tirar o que já está em `exemplos.js`, montar o texto do Copiar) vai para `P/sugestoes.js`, testável com `node:test`. O DOM fica em `P/painel.js`, que já tem `el()` e `icone()`. Separar o DOM em outro arquivo pediria mover esses dois para um terceiro módulo. Não paga numa página local.
- **R1, profiles.** `SugestoesRepository` e `RankingDeSugestoes` ficam sem `@Profile`, como `BuscaRepository` e `CasadorPorLacunas`: assim o teste de integração sobe como o `BuscaServiceTest`, sem os beans da ingestão. Só `GeradorDeSugestoes` é `@Profile("painel")`.

### 1.3 O PRD bate com o código nestes pontos (conferido)
`BuscaService.RECUO_SEG = 3.0` e o `Math.max(0, …)`; `legenda.js` com `RECUO_SEG = 3` só para as palavras soltas; `ferramentas.js` volta a `trecho.inicio` e vai até `fimSeg`, que é o fim do último trecho (`tf.inicio_seg + tf.duracao_seg` na exata, `fimDoTrecho` na aproximada); `player.js` com `start: Math.floor(...)` no primeiro vídeo e `loadVideoById` nos seguintes; `EstimadorDeTempo` com divisão igual; `GET /api/idiomas` sem profile (responde no painel); `PainelController` com o padrão `IDIOMA`; o painel serve `classpath:/painel/` e `classpath:/static/` (então `./js/exemplos.js` existe para `painel.js`); comentário de `exemplos.js` aponta para "a consulta da seção 7 de docs/spec.md"; `exemplos.js` com 27, 26 e 25 frases.

---

## 2. Verificações

### 2.1 Consulta do R1 no PostgreSQL
Postgres 16.15 num contêiner de 2 vCPU e 7 GB, `work_mem` 64 MB, schema igual ao da V1 + V2 + V4 (mesmos índices).
- **Correção, com dados de exemplo** (4 vídeos: `v1` `[thank you] [so what]` canal `a`; `v2` `[thank you so]` canal `b`; `v3` `[thank you]` sem canal; `v4` em `es`): `you so` sai só de `v2` (em `v1` atravessaria trecho); `so` conta 2 vídeos; `thank you` = 3 vezes, 3 vídeos, 3 canais (com `coalesce`; sem ele, 2); o `es` não entra; `HAVING` com mínimo 2 deixa só `so`, `thank`, `you`, `thank you`. A consulta de `falas` (seção 3.2) também foi rodada: texto dos dois trechos, início do primeiro, fim do segundo.
- **Tempo:** corpus Brown (500 textos, 1.024.028 palavras, 57.159 trechos): **16 s**, 32.890 contagens com mínimo 6. Bate com os 17 s do PRD.
- **Ranking** (protótipo em Python com a regra exata da seção 4.2): 20.412 candidatas, 2.581 pedaços. Pedaços: `a lot` 64%, `as well` 78%, `well as` 98%, `in front` 88%, `in order` 79%, `at the same` 84%, `united states` 86%. `of the` na posição 301 e `in the` na 419 (fora do topo 100). Topo: `has been, more than, at least, have been, had been, did not, new york, rather than, as well as, years ago, does not, may be, i am, must be, will be...`; entre as 60 primeiras: `at the same time, for example, too much, even though, each other, i think, based on, according to, on the other hand`.
- **D1:** os experimentos com 2 canais e com `NULL` estão na seção 1.1.

### 2.2 YouTube IFrame API (R2)
- Parâmetro `start` do player: inteiro ("positive integer"). `loadVideoById({videoId, startSeconds})`: `startSeconds` aceita fração ("float/integer"). Por isso o caminho único é o `loadVideoById`.
- `onReady` é o evento para chamar funções do player depois de criado.

### 2.3 Phosphor
`@phosphor-icons/core`, `assets/bold/copy-bold.svg` (o sprite usa o peso **bold**: o `i-check` do sprite é idêntico ao `check-bold.svg`). Path:
`M216,28H88A12,12,0,0,0,76,40V76H40A12,12,0,0,0,28,88V216a12,12,0,0,0,12,12H168a12,12,0,0,0,12-12V180h36a12,12,0,0,0,12-12V40A12,12,0,0,0,216,28ZM156,204H52V100H156Zm48-48H180V88a12,12,0,0,0-12-12H100V52H204Z`

---

## 3. Branch 1: `feature/recuo-trecho` (R2, A2)

### 3.1 Arquivos

| Ação | Caminho |
|---|---|
| modificar | `J/dto/ResultadoBusca.java`, `J/repository/BuscaRepository.java`, `J/service/BuscaService.java` |
| criar | `S/js/janela.js` |
| modificar | `S/js/estudio.js`, `S/js/ferramentas.js`, `S/js/player.js`, `S/js/legenda.js` |
| modificar | `T/service/BuscaServiceTest.java`, `T/service/BuscaAproximadaTest.java`, `T/controller/BuscaControllerTest.java` |
| criar | `TJS/janela.test.mjs` |
| modificar | `README.md` |
| sem mudança | `S/js/app.js` (`trechoMudou(null)` continua valendo), `S/js/api.js` (repassa `resultados` como vêm), `BuscaPorIdiomaTest` (não confere tempos), `EstimadorDeTempo` (A1 fica fora) |

### 3.2 O que muda em cada arquivo

**`ResultadoBusca`.**
```java
public record ResultadoBusca(String youtubeId, String titulo,
                             double inicioSeg,         // início estimado da 1ª palavra, sem recuo
                             double ultimaPalavraSeg,  // início estimado da última palavra (casada, na aproximada)
                             double falaInicioSeg,     // início do trecho da legenda da 1ª palavra
                             double falaFimSeg,        // fim do trecho da última palavra (início + duração); era o fimSeg
                             String legenda, double pontuacao, boolean exata, boolean legendaAutomatica,
                             String legendaTransliterada) { }
```

**`BuscaRepository`.**
- `Acerto(String youtubeId, String titulo, double inicioSeg, double ultimaPalavraSeg, double falaInicioSeg, double falaFimSeg, String legenda, boolean legendaAutomatica)`.
- `buscarSequencia`: a consulta de dentro troca `(tf.inicio_seg + tf.duracao_seg) AS fim_seg` por
  `o<n>.inicio_seg AS ultima_seg, t1.inicio_seg AS fala_inicio_seg, (tf.inicio_seg + tf.duracao_seg) AS fala_fim_seg`
  (`o<n>` é o alias da última palavra, o mesmo que já liga o `tf`). A de fora seleciona
  `q.youtube_id, q.titulo, q.inicio_seg, q.ultima_seg, q.fala_inicio_seg, q.fala_fim_seg, q.legenda, q.legenda_automatica`. O mapeamento passa a ler `r[0]` a `r[7]`. `ORDER BY` e `row_number` sem mudança.
- **Sai** `fimDoTrecho(long)` e **sai** `legenda(long, long, long)`. **Entra:**
```java
public record Falas(String texto, double inicioSeg, double fimSeg) { }

/** Texto dos trechos entre o primeiro e o último (inclusive), o início do primeiro e o fim do último. */
public Falas falas(long videoId, long trechoInicio, long trechoFim)
```
```sql
SELECT string_agg(x.texto, ' ' ORDER BY x.ordem),
       min(x.inicio_seg) FILTER (WHERE x.id = ?2),
       max(x.inicio_seg + x.duracao_seg) FILTER (WHERE x.id = ?3)
FROM trecho x
WHERE x.video_id = ?1
  AND x.ordem BETWEEN (SELECT ordem FROM trecho WHERE id = ?2)
                  AND (SELECT ordem FROM trecho WHERE id = ?3)
```
(`min`/`max` só escolhem o único valor que passa no `FILTER`.)

**`BuscaService`.**
- **Sai** `RECUO_SEG` e os dois `Math.max(0.0, … - RECUO_SEG)`.
- `exatos`: `new ResultadoBusca(a.youtubeId(), a.titulo(), a.inicioSeg(), a.ultimaPalavraSeg(), a.falaInicioSeg(), a.falaFimSeg(), a.legenda(), 1.0, true, a.legendaAutomatica(), transliterar(a.legenda()))`.
- `montarResultado`: `Falas f = buscaRepository.falas(videoId, c.primeira().trechoId(), c.ultima().trechoId())` e
  `new ResultadoBusca(v.getYoutubeId(), v.getTitulo(), c.primeira().inicioSeg(), c.ultima().inicioSeg(), f.inicioSeg(), f.fimSeg(), f.texto(), c.score(), false, v.isLegendaAutomatica(), transliterar(f.texto()))`.

Contrato de `GET /api/busca` depois da branch:
```json
{ "idioma": "en", "resultados": [
  { "youtubeId": "abc123XYZ_-", "titulo": "…", "inicioSeg": 12.0, "ultimaPalavraSeg": 14.0,
    "falaInicioSeg": 10.0, "falaFimSeg": 15.0, "legenda": "I really love this idea",
    "pontuacao": 1.0, "exata": true, "legendaAutomatica": false, "legendaTransliterada": null }
], "fraseTransliterada": null }
```

**`S/js/janela.js` (novo, puro, sem DOM).** As folgas de tela ficam todas aqui.
```js
const RECUO_PRIMEIRA_SEG = 2;   // primeira reprodução: até 2 s antes da frase
const RECUO_LOOP_SEG = 1;       // loop e tecla R: até 1 s antes
const FOLGA_FALA_SEG = 0.5;     // nunca antes de 0,5 s antes da fala que contém a frase
const FOLGA_FIM_SEG = 1.5;      // o loop vai até 1,5 s depois do início da última palavra
const DURACAO_PADRAO_SEG = 8;   // se os tempos vierem incoerentes (sai de ferramentas.js)

/** Onde tocar um resultado da busca: inicio (primeira vez), inicioLoop (loop e R) e fim (loop). */
export function janelaDoTrecho(r)   // r = { inicioSeg, ultimaPalavraSeg, falaInicioSeg, falaFimSeg }
// antes(recuo) = max(0, r.falaInicioSeg − FOLGA_FALA_SEG, r.inicioSeg − recuo)
// inicio = antes(RECUO_PRIMEIRA_SEG); inicioLoop = antes(RECUO_LOOP_SEG)
// fim = min(r.falaFimSeg, r.ultimaPalavraSeg + FOLGA_FIM_SEG); se !(fim > inicioLoop): inicioLoop + DURACAO_PADRAO_SEG
// → { inicio, inicioLoop, fim }
```
O `max` com o início da fala só atrasa o começo, nunca adianta além do recuo fixo (PRD).

**`S/js/estudio.js` (`mostrar`).** Calcula uma vez e repassa:
```js
const janela = janelaDoTrecho(r);
tocar(r.youtubeId, janela.inicio);
Ferramentas.trechoMudou(r.youtubeId, janela);
```

**`S/js/ferramentas.js`.**
- `trechoMudou(youtubeId, janela)`; `trechoMudou(null)` limpa como hoje.
- `trecho = { youtubeId, inicio: janela.inicioLoop, fim: janela.fim }`. O loop (`t >= trecho.fim` → `seekTo(trecho.inicio)`), o `reiniciar()` (tecla R e fim do vídeo com loop) e a barra de progresso usam essa janela.
- Sai `DURACAO_PADRAO_SEG` (foi para `janela.js`).

**`S/js/player.js`.** Um caminho só para tocar:
```js
let player = null, apiPronta = false, playerPronto = false, pedido = null, aoMudarEstado = () => {};

export const getPlayer = () => (playerPronto ? player : null);   // antes do onReady os métodos não existem
export function iniciarPlayer(aoMudar)        // onYouTubeIframeAPIReady: apiPronta = true; se há pedido, criarPlayer()
export function tocar(youtubeId, inicioSeg)   // pedido = { videoId, startSeconds: inicioSeg }
                                              // playerPronto → player.loadVideoById(pedido)
                                              // apiPronta && !player → criarPlayer()
                                              // senão espera (a API ou o onReady usam o último pedido)
function criarPlayer()                        // new YT.Player('player', { playerVars, events: { onReady, onStateChange } })
                                              // sem videoId e sem start; onReady: playerPronto = true; loadVideoById(pedido)
export function pausar()                      // igual
```
`playerVars`: `rel: 0, cc_load_policy: 0, modestbranding: 1, iv_load_policy: 3, playsinline: 1`. Saem `start` e `autoplay` (o `loadVideoById` já toca). Some o `Math.floor` e o segundo a mais no primeiro vídeo (A2). Some também o `pendente` de hoje: o pedido mais recente vale (clicar em outro resultado antes de a API carregar toca o último).

**`S/js/legenda.js`.**
- **Sai** `RECUO_SEG`.
- Em `mostrarFala`: `const soltas = !!c && r.exata === false && c.inicioSeg >= r.falaInicioSeg - 0.1 && c.inicioSeg < r.falaFimSeg;` (ver 1.2, palavras soltas).

**`README.md`.**
- Tabela do topo (linha "O vídeo toca 3 s antes da frase") e item 3 da arquitetura ("3 segundos antes da frase"): "até 2 s antes da frase".
- API: o JSON acima e, no lugar do bullet do `inicioSeg`: "`inicioSeg` e `ultimaPalavraSeg` são o início **estimado** da primeira e da última palavra; `falaInicioSeg` e `falaFimSeg`, o início e o fim das falas da legenda que as contêm. O servidor não recua: as folgas são do front."
- Dock: "loop do trecho (recomeça até 1 s antes da frase e volta logo depois da última palavra)".
- Limitações: troca "O recuo de 3 s cobre a imprecisão" por "Por isso o vídeo começa até 2 s antes (1 s no loop), nunca antes da fala que contém a frase. Se a estimativa atrasar mais que isso (fala longa com pausa), o loop pode cortar o começo da frase."

### 3.3 Testes

| Classe | Mudança |
|---|---|
| `BuscaServiceTest` (ajustar) | `deveAcharFraseQueCruzaDoisTrechosComRecuoDeTresSegundos` vira `deveAcharFraseQueCruzaDoisTrechosComOsTemposDoVideo`: `inicioSeg` 12,0 (`love`, 3ª de 3 palavras em 10–13 s), `ultimaPalavraSeg` 14,0 (`idea`, 2ª de 2 em 13–15 s; `[Music]` sai antes de contar), `falaInicioSeg` 10,0, `falaFimSeg` 15,0. O resto do teste igual. |
| `BuscaAproximadaTest` (ajustar) | `inicioSeg` 10,0 (`I`, sem recuo), `ultimaPalavraSeg` 14,333 (`idea`, 3ª de 3 em 13–15 s), `falaInicioSeg` 10,0, `falaFimSeg` 15,0. A legenda continua conferida (agora vem de `falas`). |
| `BuscaControllerTest` (ajustar) | `new ResultadoBusca("abc123XYZ_-", "Titulo", 12.0, 14.0, 10.0, 15.0, "well hello world", 1.0, true, true, "legenda latina")`; confere os quatro tempos no JSON e `jsonPath("$.resultados[0].fimSeg").doesNotExist()` (garante a troca de nome). |
| `TJS/janela.test.mjs` (novo) | 1) frase no meio de fala longa (`inicioSeg` 20, `ultimaPalavraSeg` 21, fala 15–25) → `inicio` 18, `inicioLoop` 19, `fim` 22,5; 2) frase que abre fala curta (10; 10,8; fala 10–12) → `inicio` = `inicioLoop` = 9,5 e `fim` 12 (limitado pela fala); 3) frase 1,2 s depois do começo da fala (11,2; fala 10–…) → `inicio` 9,5 (preso na fala) e `inicioLoop` 10,2; 4) começo do vídeo (0,3; fala 0,1–3) → `inicio` = `inicioLoop` = 0; 5) `falaFimSeg` ausente → `fim` = `inicioLoop` + 8. |
| Playwright (sessão, player simulado) | `loadVideoById` chamado uma vez por resultado, com `startSeconds` fracionário também no primeiro vídeo; nenhum `start` nos `playerVars`; com loop, o `seekTo` vai para `inicioLoop` ao passar de `fim`; R vai para `inicioLoop`; trocar de resultado antes do `onReady` toca o último escolhido; palavras soltas marcadas na primeira fala do resultado aproximado e em nenhuma fora dele. |

### 3.4 Critérios de aceite (do PRD)
- Frase que abre uma fala: o vídeo começa cerca de 0,5 s antes da fala.
- Frase no meio de uma fala longa: começa no máximo 2 s antes do início estimado.
- Com o loop ligado: recomeça no máximo 1 s antes e não toca mais de ~1,5 s depois do início da última palavra; R volta ao mesmo ponto do loop.
- O primeiro vídeo da sessão começa no mesmo ponto que os seguintes.
- `RECUO` não aparece mais em Java nem em `legenda.js`; as folgas estão só em `janela.js`.
- As palavras soltas da busca aproximada continuam só dentro do trecho do resultado.

### 3.5 Commits
1. `feat: busca devolve os tempos da frase e da fala sem recuo`: `ResultadoBusca`, `BuscaRepository`, `BuscaService`, ajustes em `BuscaServiceTest`, `BuscaAproximadaTest`, `BuscaControllerTest` (eles quebram com a assinatura nova, vão juntos).
2. `feat: front decide o recuo e o fim do loop num lugar so`: `janela.js`, `estudio.js`, `ferramentas.js`, `player.js`, `legenda.js`.
3. `test: cobre a janela do trecho`: `TJS/janela.test.mjs`.
4. `docs: recuo de 2 s e loop curto no README`: `README.md`.

O commit 1 sozinho deixa o front tocando sem recuo e o loop sem fim (`fimSeg` não existe mais). Os dois vão no mesmo PR.

### 3.6 Os 5 erros
- **Overengineering:** uma função pura e cinco constantes; sem campo "recuo" na API; sem mexer na estimativa (A1).
- **Repetir código:** some a constante duplicada; a janela é calculada uma vez em `estudio.js` e passada ao player e à dock; `legenda` e `fimDoTrecho` viram uma consulta.
- **Não saber fazer:** `start` inteiro e `startSeconds` fracionário conferidos na documentação da IFrame API.
- **Reinventar a roda:** o próprio `loadVideoById` toca e posiciona.
- **Juntar tudo:** backend com fatos do vídeo, front com as folgas; folgas em `janela.js`, não espalhadas em `player.js` e `ferramentas.js`.

---

## 4. Branch 2: `feature/sugestoes-exemplos` (R1, A3)

### 4.1 Arquivos

| Ação | Caminho |
|---|---|
| criar | `J/ingestao/painel/sugestoes/SugestoesRepository.java`, `RankingDeSugestoes.java`, `GeradorDeSugestoes.java`, `Sugestao.java` |
| modificar | `J/ingestao/painel/PainelController.java`, `J/service/Normalizador.java` |
| criar | `P/sugestoes.js` |
| modificar | `P/index.html`, `P/painel.css`, `P/painel.js`, `S/icons.svg`, `S/js/exemplos.js` |
| criar | `T/ingestao/painel/sugestoes/SugestoesRepositoryTest.java`, `T/ingestao/painel/sugestoes/RankingDeSugestoesTest.java`, `TJS/sugestoes.test.mjs` |
| modificar | `T/ingestao/painel/PainelControllerTest.java`, `T/service/NormalizadorTest.java` |
| modificar | `README.md` |
| sem mudança | schema e migrações, `BuscaController` (`GET /api/idiomas` serve os botões) |

### 4.2 O que muda em cada arquivo

**`SugestoesRepository` (novo, `@Repository`, `EntityManager` como o `BuscaRepository`).**
```java
public record Contagem(String frase, long vezes, int videos, int canais) { }   // frase = palavras normalizadas com um espaço
public record Totais(long palavras, int canais) { }

/** Frases de 1 a 5 palavras seguidas dentro do mesmo trecho, em pelo menos 'minimo' vídeos. */
public List<Contagem> contar(String idioma, int minimo)
/** Total de palavras do idioma (o N do NPMI) e quantos canais ele tem. */
public Totais totais(String idioma)
```
SQL de `contar` (parâmetros nomeados `:idioma` e `:minimo`; testada, seção 2.1):
```sql
WITH seq AS (
    SELECT o.video_id, coalesce(v.canal, '') AS canal, o.palavra AS p1,                 -- [D1]
           CASE WHEN lead(o.trecho_id, 1) OVER w = o.trecho_id THEN lead(o.palavra, 1) OVER w END AS p2,
           CASE WHEN lead(o.trecho_id, 2) OVER w = o.trecho_id THEN lead(o.palavra, 2) OVER w END AS p3,
           CASE WHEN lead(o.trecho_id, 3) OVER w = o.trecho_id THEN lead(o.palavra, 3) OVER w END AS p4,
           CASE WHEN lead(o.trecho_id, 4) OVER w = o.trecho_id THEN lead(o.palavra, 4) OVER w END AS p5
    FROM ocorrencia o
    JOIN video v ON v.id = o.video_id
    WHERE v.idioma = :idioma
    WINDOW w AS (PARTITION BY o.video_id ORDER BY o.posicao)
),
frases AS (
    SELECT video_id, canal, p1 AS frase FROM seq
    UNION ALL SELECT video_id, canal, p1 || ' ' || p2 FROM seq WHERE p2 IS NOT NULL
    UNION ALL SELECT video_id, canal, p1 || ' ' || p2 || ' ' || p3 FROM seq WHERE p3 IS NOT NULL
    UNION ALL SELECT video_id, canal, p1 || ' ' || p2 || ' ' || p3 || ' ' || p4 FROM seq WHERE p4 IS NOT NULL
    UNION ALL SELECT video_id, canal, p1 || ' ' || p2 || ' ' || p3 || ' ' || p4 || ' ' || p5 FROM seq WHERE p5 IS NOT NULL
),
por_video AS (
    SELECT frase, video_id, canal, count(*) AS vezes
    FROM frases
    GROUP BY frase, video_id, canal
)
SELECT frase, sum(vezes) AS vezes, count(*) AS videos, count(DISTINCT canal) AS canais
FROM por_video
GROUP BY frase
HAVING count(*) >= :minimo
```
- `lead(trecho_id, k) = trecho_id`: a k-ésima palavra seguinte está no mesmo trecho. Como as posições de um trecho são seguidas, `p3` não nulo garante `p2` não nulo.
- `por_video` agrupa por vídeo antes; então `count(*)` de fora = vídeos e `count(DISTINCT canal)` = canais.
- `sum` volta como `BigDecimal` e `count` como `Long`: mapear por `Number`.
- Sem `ORDER BY`: quem ordena é o ranking.

SQL de `totais`:
```sql
SELECT (SELECT count(*) FROM ocorrencia o JOIN video v ON v.id = o.video_id WHERE v.idioma = :idioma),
       (SELECT count(DISTINCT coalesce(canal, '')) FROM video WHERE idioma = :idioma)   -- [D1]
```

**`Sugestao` (novo).** `public record Sugestao(String frase, int videos, long vezes) { }` (é o JSON da rota).

**`RankingDeSugestoes` (novo, `@Component`, pura: sem banco, como o `CasadorPorLacunas`).**
```java
static final int LIMITE = 150;
static final double RAZAO_PEDACO = 0.5;

public List<SugestoesRepository.Contagem> ranquear(List<SugestoesRepository.Contagem> contagens,
                                                   long totalPalavras, int canaisDoIdioma)
static double associacao(String frase, Map<String, Long> vezes, long totalPalavras)   // pacote, para o teste
```
Regras, nesta ordem:
1. `vezes`: mapa frase → vezes de todas as contagens. Para cada contagem com 2 ou mais palavras: `maiorADireita[primeiras n−1]` e `maiorAEsquerda[últimas n−1]` guardam o maior `vezes` visto.
2. **Filtros:** 2 a 4 palavras; `canais >= Math.min(2, canaisDoIdioma)`.
3. **Pedaço:** descarta se `max(maiorADireita[f], maiorAEsquerda[f]) >= RAZAO_PEDACO * vezes(f)` (uma única palavra completa a frase em 50% ou mais das vezes, de um lado ou do outro). As frases de 5 palavras só servem aqui.
4. **Associação (NPMI no ponto mais fraco):** para cada corte da frase em `a | b` (n − 1 cortes),
   `npmi = ln(c(f) · N / (c(a) · c(b))) / −ln(c(f) / N)`, com `N = totalPalavras`; vale o **menor**. As subfrases sempre estão no mapa (cada ocorrência da frase contém as subfrases, então elas têm pelo menos os mesmos vídeos e passam no `HAVING`); a classe não trata ausência.
5. **Postos:** `postoVideos` = 1 + número de candidatas com **mais** vídeos (empate = mesmo posto); `postoAssociacao`, igual, com a associação.
6. **Ordem:** `postoVideos + postoAssociacao` crescente (a média é a soma dividida por 2, mesma ordem); empate: mais vídeos primeiro; depois a frase em ordem alfabética (lista estável entre cliques).
7. Devolve as `LIMITE` primeiras.

**`GeradorDeSugestoes` (novo, `@Service`, `@Profile("painel")`).**
```java
@Transactional(readOnly = true)
public List<Sugestao> gerar(String idioma, int minimo)
// totais → ranking.ranquear(repository.contar(idioma, minimo), totais.palavras(), totais.canais())
// → new Sugestao(normalizador.juntar(List.of(c.frase().split(" "))), c.videos(), c.vezes())
```

**`Normalizador`.** Novo, reaproveitando o `CARACTERE_SOLTO` que já existe:
```java
/** Junta tokens para mostrar: espaço entre eles, menos entre dois caracteres soltos (chinês e japonês). */
public String juntar(List<String> tokens)
```

**`PainelController`.** Injeta `GeradorDeSugestoes`.
```java
@GetMapping("/sugestoes")
public ResponseEntity<?> sugestoes(@RequestParam(defaultValue = "en") String idioma,
                                   @RequestParam(defaultValue = "6") int minimo)
```
- `idioma` fora do padrão `IDIOMA` → 400 `requisicao_invalida`, "Idioma inválido. Use um código como en, es ou pt." (mesma mensagem do POST).
- `minimo` fora de 1 a 100 → 400 `requisicao_invalida`, "Mínimo de vídeos: de 1 a 100.".
- Senão 200 com a lista. Idioma sem vídeo → lista vazia.
```
GET /api/painel/sugestoes?idioma=en&minimo=6
→ 200 [ { "frase": "at least", "videos": 41, "vezes": 63 }, … ]   (até 150, na ordem do ranking)
```
Síncrono: o clique espera a consulta (~17 s por milhão de palavras). Sem tarefa em segundo plano (PRD).

**`P/sugestoes.js` (novo, puro, sem DOM e sem import).**
```js
export function separarConhecidas(sugestoes, exemplos = [])
// → { novas, puladas }; compara em minúsculas e com ’ trocado por '; exemplos undefined (idioma sem lista) = nenhum
export function textoParaCopiar(sugestoes)
// uma por linha: "at least · 41 vídeos" ("1 vídeo" no singular), linhas separadas por "\n"
```

**`P/index.html`.** Nova seção depois de `#cadastradas`:
```html
<section id="sugestoes" class="card sugestoes" aria-labelledby="sugestoes-titulo">
  <div>
    <h2 id="sugestoes-titulo">Sugestões de exemplos</h2>
    <p class="dica">Frases de 2 a 4 palavras que aparecem em vários vídeos e andam juntas. Escolha um idioma, copie a lista e cole no chat.</p>
  </div>
  <div class="sugestoes-controles">
    <label class="campo-num">
      <span>Mínimo de vídeos</span>
      <input id="minimo" type="number" value="6" min="1" max="100" required>
    </label>
    <div id="sugestoes-idiomas" class="sugestoes-idiomas"></div>
  </div>
  <p id="sugestoes-resumo" class="dica" role="status" hidden></p>
  <div id="sugestoes-resultado" class="sugestoes-resultado" hidden>
    <button id="copiar" type="button" class="btn pequeno">
      <span>Copiar</span>
      <svg class="ic" aria-hidden="true"><use href="icons.svg#i-copy"/></svg>
    </button>
    <ol id="sugestoes-lista" class="sugestoes-lista"></ol>
  </div>
</section>
```

**`P/painel.css`.**
- `.sugestoes { padding: 24px; display: grid; gap: 14px; }`; o título reaproveita a regra de `.cadastradas h2` (seletor agrupado `.cadastradas h2, .sugestoes h2`).
- `.sugestoes-controles` (flex, `align-items: end`, `flex-wrap: wrap`, `gap: 16px`); `.sugestoes-idiomas` (flex, wrap, `gap: 8px`); `.sugestoes-resultado` (grid, `gap: 10px`, `justify-items: start`).
- `.sugestoes-lista`: mesma rolagem de `.cadastradas-itens` (`max-height: 540px; overflow-y: auto; scrollbar-gutter: stable`), seletor agrupado; números da lista em `tabular-nums`.
- `.sugestao-frase` (peso 600); `.sugestao-numeros` (`--suave`, `.85rem`, `tabular-nums`). Não usar `.lista*` (colide com o `style.css`, já registrado no lote 1).

**`P/painel.js`.**
- `import { EXEMPLOS } from './js/exemplos.js';` e `import { separarConhecidas, textoParaCopiar } from './sugestoes.js';`.
- `const API_SUGESTOES = '/api/painel/sugestoes';` e `let copiaveis = [];`.
- `async function carregarIdiomasDasSugestoes()`: `GET /api/idiomas`; um botão `btn pequeno` por idioma, rótulo = texto da `<option>` do `#idioma` com esse código (o código se não houver). Lista vazia → resumo "Nenhum idioma indexado ainda.". Chamada uma vez ao abrir a página.
- `async function gerarSugestoes(idioma, botao)`: lê `#minimo`; desabilita os botões de idioma e mostra "Gerando…" no clicado; `fetch(API_SUGESTOES + '?idioma=' + … + '&minimo=' + …)`; 200 → `mostrarSugestoes`; 400 → a `mensagem` no resumo; falha de rede → `SEM_PAINEL`. No fim, reabilita e volta o rótulo.
- `function mostrarSugestoes(idioma, sugestoes)`: `separarConhecidas(sugestoes, EXEMPLOS[idioma])`; `copiaveis = novas`; cada `<li>`: `<span class="sugestao-frase" lang="<idioma>">` + `<span class="sugestao-numeros">N vídeos · M vezes</span>`. Resumo: "N sugestões em <nome>. P já estavam nos exemplos e ficaram de fora." (sem a segunda frase quando `P = 0`; "Nenhuma frase passou do mínimo. Tente um mínimo menor." quando vazio). `#sugestoes-resultado` some quando não há novas.
- `async function copiar()`: `navigator.clipboard.writeText(textoParaCopiar(copiaveis))`; sucesso → o botão mostra "Copiado" e o ícone `check` por 2 s; erro → resumo "Não consegui copiar. Selecione a lista e copie à mão.".

**`S/icons.svg`.** Novo `<symbol id="i-copy" viewBox="0 0 256 256"><path d="…"/></symbol>` com o path da seção 2.3.

**`S/js/exemplos.js` (A3).** O comentário de cima passa a: "Toda frase precisa dar resultado exato no idioma: escolher das **Sugestões de exemplos** do painel de ingestão (só saem frases do índice)." Os comentários de cada idioma ficam (contam de onde veio cada lista).

**`README.md`.** Na seção do painel: "Sugestões de exemplos": um botão por idioma indexado; o mínimo de vídeos (padrão 6); como a lista é ranqueada (vídeos + associação, sem pedaços, no mesmo trecho, em 2 canais ou mais); que conta o índice inteiro e leva cerca de 17 s por milhão de palavras; que pula o que já está em `exemplos.js`; o Copiar.

### 4.3 Testes

| Classe | Cenários |
|---|---|
| `RankingDeSugestoesTest` (novo, `new RankingDeSugestoes()`) | 1) **pedaço à direita, no limite:** `a lot` 100 vezes e `a lot of` 50 → só `a lot of` fica; 2) **pedaço à esquerda:** `well as` 50 e `as well as` 49 → `well as` sai; 3) **vizinha abaixo da metade fica:** `you know` 100, `you know what` 49 → as duas ficam; 4) **associação no ponto mais fraco:** `associacao("i don't know", …)` com N = 1000, `i` 50, `don't` 20, `know` 30, `i don't` 10, `don't know` 15, `i don't know` 8 → 0,4903 (o corte `i | don't know`; o outro dá 0,680); 5) **ordem pelos postos:** N = 10.000, palavras `of` 600, `the` 900, `for` 150, `example` 32, `i` 300, `think` 80, `in` 500; frases (vezes, vídeos) `of the` (400, 50), `in the` (300, 48), `i think` (60, 30), `for example` (30, 20) → ordem `of the`, `i think`, `for example`, `in the` (postos 1+3, 3+2, 4+1, 2+4; o empate em 5 vai para quem tem mais vídeos); 6) **tamanho:** frase de 1 e de 5 palavras nunca saem; 7) **canais:** com 3 canais no idioma, frase de 1 canal sai; com 1 canal no idioma, a mesma frase fica; 8) **limite:** 151 candidatas → 150. |
| `SugestoesRepositoryTest` (novo, `@SpringBootTest` + Testcontainers como o `BuscaServiceTest`; dados pelo `IndexadorDeVideo` com canal) | Vídeos: `v1` en canal `a` `["thank you", "so what"]`; `v2` en canal `b` `["thank you so much my friend"]`; `v3` en sem canal `["thank you"]`; `v4` es canal `a` `["thank you"]`. 1) `contar("en", 1)`: `you so` com 1 vídeo (só o `v2`), `thank you so much my` presente e a frase de 6 palavras ausente, `thank you` = 3 vezes, 3 vídeos, 3 canais `[D1]`; 2) `contar("en", 2)` = exatamente `thank`, `you`, `thank you`, `so`; 3) `totais("en")` = 12 palavras e 3 canais `[D1]`. |
| `NormalizadorTest` (ajustar) | Um `@ParameterizedTest` de `juntar`: `[a, lot, of]` → `a lot of`; `[你, 好]` → `你好`; `[我, love]` → `我 love`. |
| `PainelControllerTest` (ajustar) | `@MockitoBean GeradorDeSugestoes`. 1) `GET /api/painel/sugestoes?idioma=ru&minimo=3` → 200, `$[0].frase`, `videos`, `vezes`, e `gerar("ru", 3)`; 2) sem parâmetros → `gerar("en", 6)`; 3) `@ParameterizedTest`: `minimo=0`, `minimo=101`, `idioma=../x` → 400 `requisicao_invalida` e `verifyNoInteractions(gerador)`. |
| `TJS/sugestoes.test.mjs` (novo) | `separarConhecidas`: tira as que estão nos exemplos (maiúscula e `’` não atrapalham) e conta as puladas; sem lista do idioma → nada pulado. `textoParaCopiar`: uma por linha no formato `frase · N vídeos`; `1 vídeo` no singular. |
| Playwright (sessão, API simulada) | Um botão por idioma de `/api/idiomas`, com o nome em português; "Gerando…" e botões desabilitados durante o pedido; lista e resumo com as puladas; Copiar escreve o texto esperado (`navigator.clipboard` simulado) e mostra "Copiado"; erro 400 aparece no resumo. |
| No seu banco (aceite) | Rodar a consulta da 4.2 no `psql` com `en` e mínimo 6, e o botão no painel, para conferir o tempo e o topo. |

### 4.4 Critérios de aceite (do PRD)
- Um botão para cada idioma indexado (`en`, `es`, `ru`); clicar em `en` mostra até 150 frases de 2 a 4 palavras e quantas foram puladas por já estarem em `exemplos.js`.
- No topo 50 de `en`: nenhum pedaço cuja forma longa também esteja na lista; `of the` e `in the` fora do topo 100.
- Nenhuma frase atravessa trecho; nenhuma está em menos vídeos que o mínimo; nenhuma vem de um canal só `[D1]`.
- Copiar põe a lista na área de transferência, uma por linha; buscar qualquer frase dela no site dá resultado exato.
- Com mínimo 3, o `ru` devolve candidatas.

### 4.5 Commits
1. `feat: painel conta e ranqueia sugestoes de exemplos`: `SugestoesRepository`, `RankingDeSugestoes`, `GeradorDeSugestoes`, `Sugestao`, `Normalizador` (`juntar`), `PainelController`, e no `PainelControllerTest` só o `@MockitoBean GeradorDeSugestoes` (sem ele o contexto do teste não sobe).
2. `feat: secao de sugestoes no painel com botao copiar`: `P/index.html`, `P/painel.css`, `P/painel.js`, `P/sugestoes.js`, `S/icons.svg`, `S/js/exemplos.js` (comentário).
3. `test: cobre a contagem, o ranking, a rota e a copia das sugestoes`: `SugestoesRepositoryTest`, `RankingDeSugestoesTest`, `NormalizadorTest`, os testes novos de `PainelControllerTest`, `TJS/sugestoes.test.mjs`.
4. `docs: sugestoes de exemplos no README`: `README.md`.

### 4.6 Os 5 erros
- **Overengineering:** sem tabela, sem migração, sem tarefa em segundo plano, sem biblioteca; dois números (vídeos e NPMI) e uma razão; um `HAVING` só; a poda Apriori fica registrada no PRD para quando passar de ~1 min.
- **Repetir código:** `GET /api/idiomas`, o padrão `IDIOMA` e o `ErroResposta` do painel; `EXEMPLOS` importado, não copiado; regra de escrita do `Normalizador` (`juntar`); CSS agrupado com `.cadastradas`; rótulo do idioma lido do `<select>` que já existe.
- **Não saber fazer:** NPMI (Bouma) e o espalhamento em vários subcorpora seguem a literatura citada no PRD; `navigator.clipboard` em `localhost` (contexto seguro).
- **Reinventar a roda:** o Postgres conta (janela + agregação, despeja em disco); NPMI é a fórmula padrão.
- **Juntar tudo:** consulta no repositório, ranking puro, orquestração no gerador, rota no controller do painel; no front, regra pura em `sugestoes.js` e DOM em `painel.js`.

---

## 5. Ordem de trabalho
1. Responder **D1** (e rodar a consulta de `canal` dela).
2. Confirmar o estado do Git: lote 1 com `./mvnw verify`, PRs e merge na `develop`. As branches do lote 2 saem da `develop` atualizada.
3. Branch `feature/recuo-trecho` → merge.
4. Branch `feature/sugestoes-exemplos` → merge → gerar as sugestões no seu índice e escolher os exemplos (nunca inventar frases).

---

## 6. Fontes
- YouTube IFrame Player API (`loadVideoById`, `startSeconds` fracionário, `onReady`): https://developers.google.com/youtube/iframe_api_reference
- Parâmetros do player (`start` inteiro): https://developers.google.com/youtube/player_parameters
- PostgreSQL 16, funções de janela (`lead`) e agregação com `FILTER`: https://www.postgresql.org/docs/16/functions-window.html e https://www.postgresql.org/docs/16/sql-expressions.html#SYNTAX-AGGREGATES
- NPMI (Bouma) e extração de colocações: https://en.wikipedia.org/wiki/Pointwise_mutual_information e https://en.wikipedia.org/wiki/Collocation_extraction
- Academic Formulas List (frequência + MI, presença em vários subcorpora): https://www.eapfoundation.com/vocab/academic/afl/
- Clipboard API (`writeText`, contexto seguro): https://developer.mozilla.org/en-US/docs/Web/API/Clipboard/writeText
- Phosphor Icons, `copy` bold (`@phosphor-icons/core`): https://github.com/phosphor-icons/core/blob/main/assets/bold/copy-bold.svg
- Corpus de teste (Brown, NLTK): https://raw.githubusercontent.com/nltk/nltk_data/gh-pages/packages/corpora/brown.zip
