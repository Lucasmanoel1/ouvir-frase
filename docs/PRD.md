# PRD: melhorias e novidades do OuvirFrase (lote de novembro/2026)

> Fase 1 de 3 (Pesquisa → **Spec** → Code). Este documento diz **o quê** e **por quê**, com a abordagem recomendada e o que foi descartado. A spec (fase 2) transforma isto em arquivos e mudanças exatas.
> Base: código da `develop` em 02/11/2026 (merge do PR #27), `OuvirFrase-contexto-legivel.txt` e a nota de passagem de 30/10.
> Versão 2 (02/11/2026): já com as respostas do Lucas (seção 7). Não há decisão pendente; a spec pode começar.

---

## 1. Princípios (valem para todas as fases)
1. **Overengineering:** antes de cada solução, perguntar se existe forma mais simples. Nada de lib quando 10 linhas resolvem; nada de 10 linhas quando o navegador ou uma lib consolidada já faz.
2. **Repetir código:** procurar no projeto antes de escrever (ex.: o menu de velocidade já é um popover; `listarVideos` já lista qualquer playlist; `triar` já conta pendentes).
3. **Não saber fazer:** ler a documentação oficial primeiro; se não houver, pesquisar.
4. **Reinventar a roda:** pesquisar se alguém já resolveu (ICU para transliteração, `appearance: base-select` para o seletor).
5. **Juntar tudo no mesmo lugar:** separar em arquivos e métodos quando a responsabilidade muda (ex.: separar a comparação pura da UI do microfone).

Regras de trabalho que continuam: sem commit/push (só o Lucas); ícones Phosphor no sprite, sem emojis; voz pt-BR com "você"; respeitar `prefers-reduced-motion`; testes com boa cobertura, sem redundância; avisar antes de implementar algo não pedido.

---

## 2. Requisitos: melhorias (R1 a R9)

### R1. Transliteração na prática de fala (alfabetos não latinos)
**Hoje:** o cartão de prática (`pronuncia.js`) não mostra o texto a repetir; só mostra o resultado depois de falar. Em russo o usuário vê `Привет` e não sabe como soa.

**Requisito**
- Quando o texto a repetir (aba **Frase** ou **Trecho**) tiver letras fora do alfabeto latino, mostrar a transliteração em letras latinas no cartão de prática, antes e depois de falar.
- Texto em alfabeto latino (inglês, espanhol, etc.): **não mostrar nada**.

**Abordagem recomendada: ICU4J no backend**
- Biblioteca: `com.ibm.icu:icu4j` (Unicode Consortium; padrão da indústria). Classe `Transliterator`.
  - Russo: `Russian-Latin/BGN` (padrão BGN/PCGN, o mesmo de mapas e documentos). `Привет` → `Privet`.
  - Outros (para quando forem indexados): `Any-Latin` (coreano → romanização, chinês → pinyin com tons, kana japonês → romaji).
- Decidir se precisa: percorrer as letras com `Character.UnicodeScript`; se alguma letra não for `LATIN` (ignorando `COMMON` e `INHERITED`), transliterar. Senão, `null`.
- Nova classe `service/Transliterador`. Na API: `RespostaBusca` ganha `fraseTransliterada`, `ResultadoBusca` ganha `legendaTransliterada` (ambos `null` em texto latino). O front só exibe o que vier.
- Por que no backend: a frase e o trecho já vêm de lá; Java testável com JUnit; o front segue sem build e sem biblioteca copiada.

**Descartado**
- Biblioteca JS `transliteration` copiada para `static/`: funciona, mas é código de terceiro vendorizado num front sem build, e a lógica de idioma já mora no backend.
- Tabela de letras feita à mão: reinventar a roda e errar em casos como `ё`, `й`, `щ`.

**Observações**
- "Priviet" (seu exemplo) não é padrão: o `е` depois de consoante vira `e` no BGN/PCGN → `Privet`. A transliteração indica as letras, não a pronúncia exata (o `е` russo amolece a consoante). Dizer isso num `title` ou nota pequena é opcional.
- Limitação: japonês com kanji sai em pinyin mandarim no `Any-Latin` (o ICU não sabe a leitura japonesa). Japonês não está indexado; quando for, avaliar o Kuromoji. Registrar no README.
- O jar do ICU4J tem cerca de 14 MB. Aceitável; a spec confere a versão atual e se a instância de `Transliterator` pode ser compartilhada entre threads (senão, criar por chamada ou `ThreadLocal`).

**Aceite:** buscar `привет` em russo → o cartão mostra `Привет` e `Privet`; trocar para **Trecho** mostra a transliteração do trecho; buscar `hello` em inglês → nenhuma linha extra.

---

### R2. Sublinhado acompanhando a palavra falada (efeito karaokê)
**Hoje:** `legenda.js` troca a fala inteira conforme o tempo (`aoTempo`, chamado a cada 100 ms por `ferramentas.js`). `destaque.js` aplica o marca-texto laranja na frase buscada.

**Requisito**
- Sublinhar a palavra que está sendo dita, avançando com o vídeo. Pausar congela; arrastar a barra do YouTube atualiza.
- Convive com o marca-texto laranja (o "charme" fica). O sublinhado é outra camada: traço na cor `--tinta`, abaixo da palavra.

**Restrição técnica (importante)**
- Não existe tempo por palavra. A biblioteca `youtube-transcript-api` 0.4.0 só entrega início e duração de cada trecho. O sublinhado será **estimado**: interpolação linear dentro da fala.
- Recomendado: pesar pelo **número de caracteres** de cada palavra (palavra longa demora mais), não só pela posição. É uma linha de diferença em relação ao `EstimadorDeTempo` do backend e sincroniza melhor.
- Vai haver deriva em falas com pausa no meio. Aceitável para protótipo; registrar como limitação no README.

**Abordagem**
- Ao trocar de fala: renderizar cada palavra num `<span>` uma vez (CJK: um caractere por span, igual ao Normalizador).
- A cada `aoTempo(t)`: calcular o índice da palavra e só trocar a classe (`.falando`). Nada de re-renderizar o texto a cada 100 ms.
- O marca-texto precisa continuar funcionando com os spans. A spec define se `destaque.js` passa a gerar spans por palavra (e marca pelas palavras) ou se o karaokê envolve as palavras depois do `mark`.

**Aceite:** durante a reprodução, o sublinhado avança palavra a palavra dentro da fala; não pisca; funciona com legenda manual e automática; no estado inicial da busca (antes de a legenda completa carregar) não quebra nada.

---

### R3. "Entendi:" só quando acrescenta informação
**Hoje:** `mostrarResultado` em `pronuncia.js` sempre mostra `Entendi: "..."`.

**Requisito:** mostrar só quando
- a nota for menor que 100%, **ou**
- a nota for 100% mas o usuário falou palavras a mais (ex.: alvo `welcome`, falou `welcome back` → 100% e `Entendi: "welcome back"`).

**Abordagem:** `comparar` já devolve `ouvido` (tokens ouvidos) e as palavras casadas. Palavras a mais = `ouvido.length > casadas`. Uma condição; nenhum código novo de comparação.

**Aceite:** 100% exato → sem "Entendi"; 100% com palavra extra → aparece; 60% → aparece.

---

### R4. Painel: aceitar playlist do YouTube
**Hoje:** `YouTubeCanalClient.resolverCanal` aceita `@handle`, link do canal ou id `UC...`, e usa a playlist de **uploads** do canal. `listarVideos(playlistId, ...)` já funciona para **qualquer** playlist.

**Requisito**
- O campo do painel aceita link de playlist (`...?list=PL...` ou `.../playlist?list=...`).
- A lista é registrada **como playlist**, não como canal (os vídeos vêm de vários canais).

**Abordagem**
- Detectar `list=` na entrada (regex). Buscar o título em `playlists.list?part=snippet&id=` (1 unidade de cota). Reaproveitar `listarVideos` sem mudança.
- Nome do arquivo: `data/<idioma>/playlist-<slug>.csv`. O `CatalogoDeVideos` já usa o nome do arquivo como `canal`, então o vídeo fica gravado com `canal = playlist-<slug>` sem migração.
- Generalizar sem renomear tudo: o record `Canal(titulo, uploadsPlaylistId, slug)` serve para os dois casos; a spec decide se vira `Fonte(titulo, playlistId, nomeArquivo)`.
- Slug: se o título não tiver letras ASCII (ex.: playlist em russo), o `slug()` atual devolve `canal`. Para playlist, usar o id da playlist como reserva (`playlist-PLxxxx`). Ver achado A2.
- Recusar com mensagem clara: mixes automáticos (`list=RD...`) e listas privadas ("Assistir mais tarde"), que a API não devolve.
- Texto do painel: "Cole o link do canal ou da playlist."

**Decidido:** não gravar o canal real de cada vídeo (`videoOwnerChannelTitle`). Exigiria coluna nova no CSV e talvez migração, e `video.canal` não aparece em nenhuma tela. A playlist fica registrada só pelo nome do arquivo.

**Aceite:** colar um link de playlist com vídeos de canais diferentes cria `playlist-<slug>.csv`, indexa um lote e mostra **Continuar** se sobrar.

---

### R5. Painel: ingerir listas já cadastradas
**Hoje:** todo clique passa pela descoberta. Não há como ingerir um CSV que já existe (ex.: os três de `data/ru/` montados à mão). E o **Continuar** repete a descoberta: chama a API do YouTube (canal, páginas da playlist, detalhes) a cada lote, mesmo sem vídeo novo. Gasta cota à toa.

**Requisito**
- Seção "Listas cadastradas" no painel: uma linha por CSV (idioma, nome, tipo canal/playlist, total, indexados, descartados, pendentes) e botão **Ingerir lote** quando houver pendentes.
- **Continuar** passa a só indexar o próximo lote, sem redescobrir.

**Abordagem**
- `GET /api/painel/listas`. As contagens reaproveitam a triagem que o `IngestaoService` já faz (`triar`); expor um método público que devolve as contagens por lista em vez de duplicar a lógica.
- `Pedido` ganha um modo: **descobrir e indexar** (link) ou **só indexar** (lista existente). `PainelDeIngestao.rodar` pula a descoberta no segundo modo.
- O filtro por lista precisa considerar o **idioma** também (achado A4).

**Aceite:** abrir o painel lista `en/ted`, `en/teded`, `es/tededespanol` e os três de `ru/` com as contagens; clicar em Ingerir lote numa lista russa indexa sem chamar a descoberta; Continuar não chama `channels.list`.

---

### R6. Microfone: só encerrar após ~3 s de silêncio
**Hoje:** `continuous = false`. O Chrome encerra o reconhecimento na primeira pausa curta; travar 1 s numa palavra já corta.

**Requisito**
- Encerrar sozinho depois de cerca de **3 s sem fala nova**.
- Encerrar na hora quando o usuário clicar de novo no botão (já existe: vira "Parar").

**Abordagem (documentada na Web Speech API)**
- `continuous = true` e `interimResults = true`.
- Timer de silêncio (`SILENCIO_MS = 3000`) reiniciado a cada `onresult` (inclusive os parciais). Estourou → `rec.stop()`.
- Juntar os resultados finais de todos os segmentos (`ev.results[i]`), porque no modo contínuo a fala chega em pedaços. Alternativas (`maxAlternatives`) só fazem sentido quando houver um segmento; com vários, usar a melhor de cada.
- Antes da primeira palavra: deixar o próprio `no-speech` do navegador agir ou usar um teto maior (ex.: 8 s). Teto total de segurança (ex.: 30 s).
- Separar arquivos: `comparacao.js` (funções puras `tokens` e `comparar`, testáveis) e `pronuncia.js` (microfone e tela). Hoje está tudo junto em 192 linhas.

**Riscos:** o Chrome no Android tem histórico de duplicar resultados no modo contínuo; testar no celular. Safari tem suporte parcial (o site já avisa quando não há suporte).

**Aceite:** falar "thank you … (pausa de 1,5 s) … very much" → uma tentativa só, com a frase inteira; ficar 3 s calado depois de falar → encerra e mostra o resultado; clicar em Parar → encerra na hora.

---

### R7. "Experimente" por idioma, com troca de exemplos
**Hoje:** `historico.js` tem `EXEMPLOS = { en: [3 frases] }`. Os **Recentes** mostram buscas de todos os idiomas e o clique troca o idioma.

**Requisito**
- No mínimo **20 frases por idioma** indexado. Mostrar 3. Botão para trocar por outras 3 (ícone Phosphor `shuffle` ou `arrows-clockwise`, copiado para o sprite), sem repetir até esgotar a lista.
- Trocar o idioma troca os exemplos.
- **Recentes** mostram só buscas do idioma atual (a lógica de trocar idioma ao clicar no recente deixa de ser necessária e sai).
- Idioma sem lista: a seção some (comportamento atual).

**Abordagem**
- Dados num módulo próprio, `static/js/exemplos.js` (só dados; `historico.js` fica com a lógica). Sem `fetch`, coerente com o resto do front.
- **Toda frase precisa ter resultado no índice.** Exemplo que dá "Não encontrei" é pior que nenhum. Curadoria a partir do próprio índice: a spec escreve uma consulta SQL de sequências de 2 a 3 palavras mais frequentes por idioma (self-join em `ocorrencia`); o Lucas escolhe as naturais. Isso também resolve "quais idiomas têm lista": os que estão indexados de fato.

**Aceite:** inglês mostra 3 de ≥ 20; o botão troca; mudar para espanhol troca exemplos e recentes; nenhum exemplo devolve vazio.

---

### R8. Seletor de idioma com a cara do projeto
**Hoje:** `<select>` nativo dentro da pílula `.idioma`; a lista aberta é o padrão do sistema (imagem do Lucas).

**Decidido: `<select>` customizável (`appearance: base-select`), só CSS.** O pedido é estético e simples.
- Recurso novo do HTML/CSS, no Chrome/Edge desde a versão 135. Estiliza o próprio `<select>`, inclusive a lista aberta (`::picker(select)`), a opção marcada (`option:checked`, `::checkmark`) e a seta (`::picker-icon`).
- Visual: a lista aberta com o mesmo estilo do menu de velocidade (`.popover card`: traço 2 px, sombra dura, raio 12, opção marcada em `--laranja-claro` com check).
- **Zero JavaScript novo**; teclado e leitor de tela continuam nativos. O painel usa a mesma pílula `.idioma` e ganha junto.
- Navegador sem suporte: mostra o select nativo, ou seja, o que existe hoje. Não quebra. O site já é pensado para Chrome/Edge (microfone e tradução).
- A spec confere em webstatus.dev o estado atual no Firefox e no Safari (não confirmei).

**Descartado:** menu próprio em JS (popover com `role="menu"`). Ficaria igual em todos os navegadores, mas exige teclado e ARIA à mão: mais código para um ganho só estético.

**Aceite:** no Chrome/Edge a lista aberta tem o visual do projeto (cartão, traço, sombra dura, opção marcada em laranja-claro com check); no Firefox/Safari continua funcionando com a lista nativa; o painel herda o mesmo visual.

---

### R9. Detectar sozinho o idioma da frase
**Hoje:** a busca usa só o idioma do seletor/URL. Frase em espanhol com o seletor em inglês → "Não encontrei".

**Requisito:** se a frase for de outro idioma indexado, buscar nele e trocar o seletor (e o `l=` da URL). **Sem mensagem de aviso.** No máximo um sinal discreto na pílula de idioma (ex.: um ponto laranja ou um pulso curto) que some na próxima interação. Com `prefers-reduced-motion`, sem animação: só o ponto.

**Abordagem recomendada: escrita + o próprio índice (backend, sem biblioteca)**
1. **Escrita da frase** (`Character.UnicodeScript`) → idiomas candidatos entre os indexados: cirílico → `ru`; hangul → `ko`; hiragana/katakana → `ja`; han → `zh-CN` ou `ja`; latino → `en, es, fr, de, it, pt`.
2. Se o idioma escolhido é compatível com a escrita, buscar nele primeiro. Se der zero resultados, ou se não for compatível, tentar os outros candidatos na ordem de tamanho do índice (`idiomasDisponiveis()` já ordena) e ficar com o primeiro que tiver resultado **exato**; aproximado só como último recurso.
3. `RespostaBusca` ganha `idioma` (o usado). O front atualiza seletor, URL e histórico com ele.

**Por quê**
- Detectores estatísticos erram muito em palavra solta. O próprio README do Lingua (a melhor biblioteca Java para texto curto) mostra cerca de 55% de acerto para uma palavra em inglês. O índice não erra: ou a frase existe naquele idioma ou não.
- Respeita a escolha do usuário quando a frase existe nos dois idiomas (ex.: `no`, `hotel`).
- Zero dependência. Custo extra só quando a primeira busca vem vazia.

**Descartado**
- **Lingua** (`com.github.pemistahl:lingua` 1.2.2): fraco em palavra única e pesado em memória com vários idiomas; ruim para o EC2 pequeno planejado.
- **Language Detector API do Chrome**: só Chrome 138+ de computador, e a própria documentação diz que frases muito curtas dão resultado pouco confiável.

**Aceite:** seletor em inglês, buscar `muchas gracias` → resultados em espanhol, seletor muda e a pílula mostra o sinal (sem mensagem); buscar `привет` com o seletor em inglês → vai direto ao russo; buscar `no` em inglês → fica no inglês.

---

## 3. Novidades: opinião e recomendação (decisões do Lucas)

### N1. Quiz (quebra-cabeça de palavras e áudio)
**Opinião sincera:** vale a pena, mas **não os dois modos separados agora**.
- O modo **só texto** (frase com lacunas e banco de palavras) é o que Duolingo e afins já fazem melhor. Sozinho, acrescenta pouco ao OuvirFrase.
- O modo **áudio** usa o que só o OuvirFrase tem: trecho real, com pessoa real falando. É o "desafio de escuta" que você mesmo pôs como prioridade em 31/10.

**Proposta enriquecida: um modo só, "Ditado com lacunas"**
- Toca o trecho real (a fala inteira, 3 a 8 s). Mostra a legenda com 1 a 3 lacunas.
- Fácil: banco de palavras (o seu quebra-cabeça). Difícil: digitar.
- Ouvir de novo, ouvir a 0,75×, revelar, e ir para o vídeo no estúdio.
- "Frase já vista": usar o histórico local (`localStorage`) para priorizar trechos de buscas que o usuário fez. **Não precisa de login** para a v1 (pontuação local).

**Regras de qualidade (sem elas o quiz ensina errado)**
- Só **legenda manual**: a automática erra e o gabarito sairia errado.
- Recortar pela fala inteira, não por palavra (o tempo de palavra é estimado e cortaria sílabas).
- Lacuna em palavra de conteúdo (não `the`, `a`); distratores do mesmo vídeo ou entre as palavras frequentes do idioma.
- Backend: endpoint de sorteio de trechos por idioma, com filtro de legenda manual e tamanho.

**Decidido:** é a próxima novidade, depois deste lote, com PRD próprio. O modo só de texto foi descartado: não competir com o Duolingo, investir no diferencial.

### N2. Reconhecimento de fala na nuvem (Azure/Google)
**Opinião sincera**
- O problema não é a qualidade da transcrição. O Web Speech do Chrome já manda o áudio para o Google. Trocar por outra transcrição melhora pouco.
- O defeito para quem estuda é outro: o reconhecedor **corrige para a palavra mais provável**, então uma pronúncia ruim passa como certa e o feedback engana.
- O que muda o jogo é **avaliação de pronúncia**: o Azure tem o *Pronunciation Assessment* (notas de precisão, fluência e completude, por palavra e por fonema). Ele é cobrado como speech-to-text padrão, sem taxa extra.
- Cota grátis (F0): 5 h de áudio por mês. Vi esse número numa resposta da comunidade Microsoft; **confirmar na página oficial de preços**. 5 h ≈ 3.600 tentativas de 5 s.
- **Atraso:** o que o usuário sente é a espera de silêncio (seus 3 s do R6), não a API. No reconhecimento contínuo o resultado final costuma chegar logo após o fim da fala. Não medi; medir num protótipo antes de decidir.
- **Arquitetura quando vier:** a chave nunca vai para o front. O backend emite um token curto para o SDK do navegador e aplica o limite por IP antes de emitir. Limite por IP é fácil de contornar (IP muda), mas serve para protótipo.

**Recomendação:** adiar, como você disse (mesma fase de login e plano pago). Quando vier, fazer como avaliação de pronúncia, não como transcrição. Conferir antes a lista de idiomas com Pronunciation Assessment (russo incluído?).

---

## 4. Achados no código (fora dos pedidos; aprovados: entram no lote)
- **A1.** O painel oferece `zh`, mas a pasta e o código do YouTube usados no resto do projeto são `zh-CN`. Ingerir chinês pelo painel criaria `data/zh/`, que não casa com o resto.
- **A2.** `YouTubeCanalClient.slug()` devolve `canal` quando o título não tem letras ASCII e não há handle. Dois canais russos colados pelo id `UC...` gravariam no mesmo `canal.csv`. Usar o id como reserva.
- **A3.** O **Continuar** redescobre o canal a cada lote e gasta cota (resolvido pelo R5).
- **A4.** `IngestaoService.executar(canal, ...)` filtra só pelo nome do arquivo; o mesmo nome em duas pastas de idioma seria misturado. Filtrar por idioma também.
- **A5.** A tokenização por regex está repetida em `destaque.js` e `pronuncia.js`. Com o R2 e o R6 mexendo nos dois, juntar num lugar só.

---

## 5. Entregas sugeridas (uma branch por assunto)
Ordem pensada para destravar dados primeiro: R7 e R9 só dão para testar de verdade com espanhol e russo indexados, e para isso o R5 precisa existir.

| Ordem | Branch | Itens |
|---|---|---|
| 1 | `feature/painel-listas` | R5, R4, A1 a A4 |
| 2 | `feature/pratica-fala` | R6, R3, R1, A5 (parte) |
| 3 | `feature/idioma-auto` | R9, R8, R7 |
| 4 | `feature/legenda-karaoke` | R2, A5 (parte) |

Entre a 1 e a 3: rodar a ingestão de `es` e `ru` pelo painel.

---

## 6. Fora deste lote
Login, plano pago, quiz (N1, próximo PRD), reconhecimento na nuvem (N2), deploy, revisão de fala com fonética (abandonada; talvez no futuro), tempo por palavra real (exigiria trocar a fonte de legendas).

---

## 7. Decisões do Lucas (02/11/2026)
1. **R3:** "Entendi" só quando há erro ou palavras a mais.
2. **R4:** playlist registrada do jeito mais simples: só pelo nome do arquivo `playlist-<slug>`.
3. **R8:** só estética, do jeito mais simples: `base-select` em CSS.
4. **R9:** troca automática **sem mensagem**; no máximo um sinal no botão de idioma. Quando a frase existe nos dois idiomas, vale o escolhido (regra do R9 aprovada).
5. **A1 a A5:** entram no lote.
6. **Fonética:** abandonada por ora.
7. **N1:** modo único "Ditado com lacunas", como próxima novidade.

---

## 8. Fontes
- ICU Transliterator (IDs como `Russian-Latin/BGN`, `Any-Latin`): https://unicode-org.github.io/icu/userguide/transforms/general/ e exemplo de uso em https://gist.github.com/JamoCA/ec4617b066fc4bb601f620bc93bacb57
- Lingua (precisão em palavra única e frases): https://github.com/pemistahl/lingua
- Language Detector API do Chrome: https://developer.chrome.com/docs/ai/language-detection
- `<select>` customizável: https://developer.chrome.com/blog/a-customizable-select e https://developer.chrome.com/docs/modern-web-guidance/guides/forms/branded-select-styling
- Web Speech API (continuous, interimResults): https://developer.chrome.com/blog/voice-driven-web-apps-introduction-to-the-web-speech-api
- YouTube Data API, playlistItems (`videoOwnerChannelTitle`): https://developers.google.com/youtube/v3/docs/playlistItems
- Azure Speech, preços: https://azure.microsoft.com/en-us/pricing/details/speech/ e Pronunciation Assessment (resposta da comunidade): https://learn.microsoft.com/en-us/answers/questions/5608069/pricing-and-usage-of-pronunciation-assessment-feat
