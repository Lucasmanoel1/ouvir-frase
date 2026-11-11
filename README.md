<div align="center">

<img src="src/main/resources/static/favicon.svg" width="96" alt="Logo do OuvirFrase: balão de fala laranja com uma onda sonora">

# OuvirFrase

**Busque. Ouça. Repita.**

Clone do [YouGlish](https://youglish.com) para treinar a escuta: digite uma palavra ou frase<br>e ouça trechos reais de vídeos do YouTube, com a legenda destacada.<br>
Sem anúncios. Sem cadastro.

![Java](https://img.shields.io/badge/Java-21-E17F53?labelColor=2B2018&style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-E17F53?labelColor=2B2018&style=flat-square)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-E17F53?labelColor=2B2018&style=flat-square)
![Status](https://img.shields.io/badge/status-V1_local-F6DCCD?labelColor=2B2018&style=flat-square)

[Como funciona](#como-funciona) · [Rodando localmente](#rodando-localmente) · [Ingestão](#ingestão) · [API](#api) · [Front](#front) · [Limitações](#limitações-conhecidas)

</div>

> **Status:** V1 (MVP) funcional localmente. O deploy em produção ainda não foi feito.

<br>

| Busque | Ouça | Repita |
|---|---|---|
| Digite uma palavra ou frase em qualquer idioma indexado. | O vídeo começa um pouco antes da frase, com loop e velocidade de 0,25× a 1,5×. | Fale no microfone e veja, palavra por palavra, o que o navegador entendeu. |

---

## Como funciona

```
CSV de vídeos → filtro embeddable → legenda → índice invertido (PostgreSQL) → busca → front
└───────────── ingestão (offline) ──────────────────────────────┘   └── online ──┘
```

1. **Ingestão (offline).** Listas curadas de vídeos (`data/<idioma>/<canal>.csv`) são filtradas (só vídeos incorporáveis). A legenda (manual, ou a automática quando não há manual) é baixada, normalizada e gravada em um **índice invertido**.
2. **Busca (online).** A frase digitada passa pela mesma normalização e é procurada no índice. O usuário nunca consulta o YouTube: a busca lê só o banco.
3. **Front.** Uma página estática que toca o vídeo (YouTube IFrame Player API) um pouco antes da frase e destaca a frase na legenda.

### Índice invertido (feito à mão no PostgreSQL)

| Tabela | Papel |
|---|---|
| `video` | Um vídeo indexado (`youtube_id` único), com canal, idioma, origem da legenda (`legenda_automatica`). |
| `trecho` | Cada trecho de legenda: texto, início, duração. |
| `ocorrencia` | **O índice:** uma linha por palavra, com `palavra`, `posicao` e `inicio_seg`. |
| `video_descartado` | Vídeos que a ingestão já rejeitou (`youtube_id`, `motivo`, `data`), para não reconsultar. |

- `posicao` é o contador de palavras do **vídeo inteiro** (não reinicia a cada trecho), o que permite achar frases que cruzam dois trechos.
- Índice B-tree em `ocorrencia (palavra, video_id, posicao)`.
- **Busca exata:** acha as ocorrências da 1ª palavra e exige as seguintes em `posicao + 1`, `+ 2`… no mesmo vídeo (self-join). Ver `BuscaRepository`.
- A mesma função de normalização (`Normalizador`) é usada no índice e na frase digitada.

### Stack

Java 21 · Spring Boot 4.1 (MVC, Data JPA, Validation, Actuator) · PostgreSQL 16 · Flyway · Maven · Lombok · Apache Commons CSV · [youtube-transcript-api](https://github.com/Thoroldvix/youtube-transcript-api) · JUnit 5 + Mockito + AssertJ + Testcontainers · GitHub Actions · Docker.

---

## Rodando localmente

Pré-requisitos: JDK 21 e Docker.

```bash
docker compose up -d          # PostgreSQL na porta 5433
./mvnw spring-boot:run        # sobe a aplicação em http://localhost:8080
```

As migrações do Flyway rodam na subida. O banco começa vazio: rode a ingestão (próxima seção).

Para consultar o banco direto (por exemplo, conferir o que foi indexado):

```bash
docker compose exec db psql -U postgres -d ouvirfrase
```

---

## Ingestão

A ingestão roda **offline**, num perfil à parte, e nunca no site publicado: o site em si não consulta o YouTube. Quem roda é o **painel** local: você cola o link de um canal ou de uma playlist e ele faz o resto.

### O painel

Uma página onde você cola o link de um canal ou de uma playlist e a ingestão roda sozinha. Ela só existe no seu computador: é o perfil `painel`, que escuta apenas em `127.0.0.1:8081`. O site publicado não tem essa página nem essa API.

**Antes de subir:** o banco no ar (`docker compose up -d db`) e a chave da API no `.env`.

**Chave da API.** Crie uma chave da **YouTube Data API v3** (Google Cloud) e coloque em `.env` na raiz. O arquivo é ignorado pelo git e só é lido no perfil `ingestao`; a variável de ambiente `YOUTUBE_API_KEY` também serve.

```
YOUTUBE_API_KEY=sua-chave
```

1. Suba o painel. Mantenha as aspas e a ordem `ingestao,painel`:

   ```bash
   ./mvnw "-Dspring-boot.run.profiles=ingestao,painel" spring-boot:run
   ```

2. Abra <http://localhost:8081>.
3. Cole o link do canal (`https://www.youtube.com/@TEDTalks`) ou da playlist (`https://www.youtube.com/playlist?list=PL…`), escolha o idioma e clique em **Ingerir**.
4. Acompanhe na tela. No fim aparece o relatório. Se sobrarem vídeos ("Faltam N vídeos"), clique em **Continuar** para o próximo lote.
5. Terminou? `Ctrl+C` no terminal.

Cada clique em **Ingerir** faz duas coisas: descobre os vídeos novos do canal ou da playlist (grava `data/<idioma>/<canal>.csv`, ou `data/<idioma>/playlist-<nome>.csv` no caso de playlist) e indexa **um lote** dessa lista (15 vídeos, com 20 s entre eles). Em **Opções** você muda quantos vídeos descobrir (padrão 100) e o tamanho do lote (padrão 15). Recarregar a página não interrompe nada: ela volta a mostrar o andamento.

Playlist pode ter vídeos de canais diferentes: todos vão para o mesmo CSV. Mixes automáticos (`list=RD…`) e listas pessoais (Assistir mais tarde, Gostei) são recusados, porque a API do YouTube não lê essas listas: use uma playlist pública.

**Listas cadastradas.** Abaixo do formulário, o painel lista cada CSV de `data/` com o tipo (canal ou playlist), o idioma e as contagens: indexados, descartados e pendentes. **Ingerir lote** indexa um lote da lista sem descobrir nada de novo no YouTube. Depois do lote, **Continuar** (e **Tentar de novo**, se o YouTube bloquear) também só indexam: não gastam cota de descoberta. O total de uma lista não conta o vídeo que já aparece antes em outro CSV (cada vídeo fica só na primeira lista, em ordem de caminho), então pode ser menor que o número de linhas do arquivo. A lista só aparece enquanto tiver vídeo pendente (lista concluída sai) e o painel mostra até 6 por vez: para ver as outras, role dentro do cartão.

**Sugestões de exemplos.** No fim da página, um botão por idioma indexado gera candidatas a frase de exemplo tiradas do próprio índice (cada uma dá resultado exato na busca). Escolha o **mínimo de vídeos** (padrão 6; baixe para idiomas com poucos vídeos, como o russo) e clique no idioma: a lista traz até 150 frases de 2 a 4 palavras, ordenadas por em quantos vídeos aparecem e por quanto as palavras andam juntas. Ficam de fora os pedaços (`a lot` quando `a lot of` é a forma completa), as frases que atravessam dois trechos da legenda e as que só aparecem em uma lista cadastrada (o bordão de um canal). A conta usa o índice inteiro do idioma e leva cerca de 17 s por milhão de palavras. O que já está em `static/js/exemplos.js` não entra (o painel diz quantas foram puladas). **Copiar** põe a lista na área de transferência, uma frase por linha (`frase · N vídeos`), pronta para colar no chat.

Se o YouTube bloquear o IP (`429`), a tela avisa e o que já foi indexado fica salvo. Espere **horas** e clique em **Tentar de novo**.

> [!WARNING]
> **Rode um lote por vez e espere entre eles.** O YouTube bloqueia IPs que fazem muitas requisições (erro `429`). Nos testes, uma segunda execução poucos minutos depois de um lote de 15 vídeos já recebeu `429`. Se acontecer, espere **horas**, não minutos.

### Como a ingestão se comporta

- **Vídeos já indexados são pulados.** Rodar de novo não duplica nada.
- **Problema do vídeo** (não encontrado, não incorporável, sem legenda manual nem automática, sem palavras aproveitáveis) → descarta com motivo e segue. O descarte é gravado em `video_descartado`: nas próximas rodadas o vídeo é pulado sem chamar o YouTube e sem gastar o limite do lote. Para reavaliar um vídeo, apague a linha dele: `DELETE FROM video_descartado WHERE motivo = 'SEM_LEGENDA';` (ou `youtube_id = '...'`).
- **Falha de infraestrutura** (chave inválida, cota, **bloqueio de IP pelo YouTube**) → para tudo e mostra o relatório parcial. Basta esperar e clicar em **Tentar de novo**.
- **Legenda automática.** Quando o vídeo não tem legenda manual, a automática é aceita e a origem fica registrada em `video.legenda_automatica`. O log marca "(legenda automática)".
- Ao final, o relatório mostra quantos entraram, quantos já existiam e quantos foram descartados (e por quê):

```
Relatório da ingestão
  Na lista: 207
  Indexados agora: 15
  Já indexados (pulados): 28
  Já descartados antes (pulados): 0
  Adiados pelo limite (rode de novo): 164
  Descartados agora: 0
```

---

## Idiomas

Uma pasta por idioma em `data/`: `en`, `es`, `fr`, `de`, `it`, `ja`, `ko`, `zh-CN`, `ru`. O nome da pasta (e a coluna `idioma` do CSV) é o **código que o YouTube usa nas legendas** (por exemplo `zh-CN`, `pt-BR`), porque é com ele que a legenda é procurada. Para um idioma novo, crie a pasta e o CSV; o seletor do front aparece sozinho quando há mais de um idioma indexado.

---

## API

### Busca

`GET /api/busca?frase=...&idioma=en` (`idioma` é opcional, padrão `en`). `GET /api/idiomas` lista os idiomas que têm vídeos indexados.

```json
{ "idioma": "en", "resultados": [
  { "youtubeId": "abc123XYZ_-", "titulo": "…", "inicioSeg": 12.0, "ultimaPalavraSeg": 14.0,
    "falaInicioSeg": 10.0, "falaFimSeg": 15.0, "legenda": "texto do trecho",
    "pontuacao": 1.0, "exata": true, "legendaAutomatica": false, "legendaTransliterada": null }
], "fraseTransliterada": null }
```

- `idioma` é o idioma em que a busca foi feita de verdade. Se a frase não existe no idioma pedido (ou está em outra escrita, como `привет` com `idioma=en`), o servidor procura nos outros idiomas indexados que escrevem assim: primeiro onde há resultado **exato**, e só depois onde há aproximado. Quando a frase existe nos dois idiomas (`no`, `hotel`), vale o pedido. Não usa biblioteca de detecção: a escrita (`Character.UnicodeScript`) e o próprio índice decidem.
- `inicioSeg` e `ultimaPalavraSeg` são o início **estimado** da primeira e da última palavra; `falaInicioSeg` e `falaFimSeg`, o início e o fim das falas da legenda que as contêm (o fim nunca passa do início da fala seguinte: na legenda automática a duração costuma invadir a próxima). O servidor não recua: as folgas são do front.
- As frases exatas vêm primeiro (`exata: true`); depois as aproximadas, com `pontuacao` = palavras casadas / total (mínimo 0,7).
- `legendaAutomatica` indica a origem da legenda. Na busca, os vídeos de legenda manual vêm antes.
- `fraseTransliterada` e `legendaTransliterada` trazem o texto em letras latinas quando ele está em outra escrita (russo, coreano, chinês...); em texto já latino vêm `null`. Usam o ICU4J (`Russian-Latin/BGN` para `ru`, `Any-Latin` para o resto).
- Sem resultado: `resultados` vazio.
- Limites: a frase tem até 200 caracteres e **15 palavras** (contadas depois da normalização; em chinês e japonês, 15 caracteres). O idioma tem até 10 caracteres.
- Tempo: a busca inteira tem um orçamento de 2,5 s (`busca.orcamento`). Passou disso, o banco cancela a consulta em andamento e a API devolve o que já achou (as frases exatas) ou uma lista vazia, em vez de deixar a tela esperando.
- Erros, sempre em JSON `{ "erro", "mensagem" }`:

| Situação | Status | `erro` |
|---|---|---|
| Frase ausente, em branco ou com mais de 200 caracteres; idioma inválido | `400` | `requisicao_invalida` |
| Mais de 15 palavras ("Use até 15 palavras.") | `400` | `frase_longa` |
| Consulta lenta demais, pool de conexões esgotado ou banco fora | `503` | `indisponivel` |

### Legenda completa do vídeo

`GET /api/videos/{youtubeId}/legenda` devolve a legenda completa do vídeo, em ordem, para o front acompanhar o vídeo (com cache de 1 dia no navegador):

```json
[ { "texto": "so what do you think", "inicioSeg": 12.4, "duracaoSeg": 2.1 } ]
```

Vídeo desconhecido: lista vazia.

---

## Front

Página estática (HTML, CSS e JavaScript em módulos ES nativos, sem build) servida pelo próprio Spring. Identidade visual, cores, tipografia e voz em [`docs/identidade-visual.md`](docs/identidade-visual.md).

- **Tela inicial:** busca, seletor de idioma, exemplos do idioma escolhido (mostra 3 por vez; o botão com setas troca por outros), buscas recentes só do idioma atual (guardadas só no navegador) e o ritmo "Busque. Ouça. Repita.".
- **Estúdio (depois da busca):** player com barra de progresso do trecho, a dock de controles, a legenda com a frase destacada, a faixa de prática e a lista de todas as ocorrências (miniatura, trecho, selo de frase aproximada). No desktop tudo cabe numa tela sem rolar; no celular os controles ficam acima da dobra.
- **Legenda que acompanha o vídeo:** o cartão mostra a fala do instante atual (também com o vídeo pausado ou depois de arrastar a barra do YouTube), com a frase buscada destacada. Se a API da legenda falhar, o cartão continua mostrando o trecho do resultado. Uma **barra laranja sob a palavra falada** acompanha a fala (também pausado ou depois de arrastar a barra). O tempo vem do player do YouTube e é lido a cada quadro da tela (`requestAnimationFrame`), por um relógio que anda com a velocidade do vídeo e não treme quando o player corrige o tempo (`relogio.js`). O marca-texto laranja continua na frase buscada, que agora casa só palavras inteiras.
- **Tradução no navegador:** abaixo da fala, em fonte menor, a tradução para português. Ela é feita **no seu navegador**, pela [Translator API](https://developer.chrome.com/docs/ai/translator-api) do Chrome: sem servidor, sem custo e sem limite de requisições. Na primeira vez o botão com o ícone de tradução mostra "Ativar tradução" e baixa o modelo do idioma (com o progresso na tela); depois liga e desliga (a escolha fica guardada no navegador) e traduz a fala atual e as duas seguintes. Onde a API não existe, o botão e a linha somem.
- **Selo "Legenda automática":** aparece no estúdio quando o trecho vem de legenda gerada por reconhecimento de voz, que pode ter erros.
- **Dock do player:** play/pause, voltar e avançar 2 s, loop do trecho (um clique: repete só este resultado, com o ícone de "1", e desliga ao trocar de resultado; clique duplo: loop automático, que continua nos próximos resultados; um clique desliga) e velocidade de 0,25× a 1,5× (lembrada no navegador).
- **Pronúncia:** o botão de microfone fica sempre visível. O usuário repete a frase buscada (ou o trecho inteiro); o navegador reconhece a fala (Web Speech API) e a página compara palavra por palavra, com porcentagem e as palavras que faltaram. O microfone junta as falas com pausas curtas numa tentativa só e encerra depois de cerca de 1,5 s de silêncio (ou ao clicar em Parar). A linha "Entendi" só aparece quando traz algo a mais que a nota. Em outro alfabeto (russo, coreano...), o cartão de prática mostra a frase ou o trecho também em letras latinas. A comparação é feita no navegador; o servidor só devolve a transliteração. Só funciona no Chrome e no Edge e exige HTTPS fora do `localhost`.
- **Idioma:** a pílula de idioma fica sempre visível; vira um botão (com seta) quando há mais de um idioma indexado. Se a busca cair em outro idioma (ver `idioma` na API), o seletor e o `l=` da URL mudam sozinhos, sem mensagem: a pílula só ganha um ponto laranja, que some na próxima interação. A lista aberta do seletor usa o visual do projeto (cartão, traço e sombra duros), só com CSS.
- **Link da busca:** `?q=frase&l=idioma`; o botão de link copia o endereço da busca atual.
- **Vídeo indisponível:** se o player recusar o vídeo (removido, privado ou com incorporação bloqueada: erros 100, 101 e 150 da IFrame API), o vídeo todo fica marcado na lista (esmaecido, miniatura cinza), o estúdio pula para o próximo disponível com um aviso e as setas passam a ignorar os marcados. Se nenhum sobrar, aparece uma mensagem. Os outros erros do player (2, 5 e 153) continuam sendo mostrados pelo próprio YouTube. A marcação vale só para a busca atual.
- **Falhas de rede:** a busca desiste depois de 15 s e mostra a mensagem de erro padrão. Se o script do player do YouTube não carregar (bloqueador de conteúdo, rede), um aviso aparece junto do resultado.
- **Privacidade:** a página [`privacidade.html`](src/main/resources/static/privacidade.html), linkada no rodapé, diz o que o servidor guarda (log de acesso com IP, rota e frase, por 14 dias), o que vai para outras empresas (player do YouTube e serviço de voz do navegador) e o que fica só no aparelho (`localStorage`). O cartão de prática avisa que o áudio vai para o serviço de voz do navegador.

**Créditos.** Ícones: [Phosphor Icons](https://phosphoricons.com) (MIT). Fontes auto-hospedadas: [Inter](https://rsms.me/inter/) e [Lora](https://github.com/cyrealtype/Lora-Cyrillic) (SIL OFL 1.1), via pacotes `@fontsource`. Miniaturas dos vídeos vêm de `i.ytimg.com`.

---

## Testes

```bash
./mvnw verify
```

Testes unitários (Normalizador, estimador de tempo, leitor de CSV, filtro, provider de legenda, serviço de ingestão) e de integração com Testcontainers (indexação e busca). Precisa do Docker. O CI roda tudo a cada push/PR para `main` e `develop`.

As funções puras do front (`palavras.js`, `comparacao.js`) têm testes com o `node:test`, embutido no Node (sem dependências). O `./mvnw verify` não roda estes:

```bash
node --test "src/test/js/*.test.mjs"
```

## Produção

O site roda numa VPS Linux: Nginx com HTTPS na frente, o app e o PostgreSQL em contêineres (`infra/docker-compose.yml`) e a imagem publicada no GitHub Container Registry a cada tag `v*`. Em produção o app só lê o banco (papel `ouvirfrase_app`, com limite de 3 s por consulta) e o índice chega como um dump gerado no computador de quem faz a ingestão. O passo a passo, do servidor vazio ao HTTPS, mais publicar uma versão, voltar atrás, atualizar o índice e ler os logs, está em [`docs/operacao.md`](docs/operacao.md).

## Estrutura

```
src/main/java/com/lucasmanoel/ouvirfrase/
  controller/  service/  repository/  model/  dto/   # a API de busca (camadas)
  ingestao/                    # pipeline offline (perfil "ingestao"): serviço, runner, filtro, IngestaoProperties
    catalogo/                  #   leitura dos CSVs e ListaDeVideos (idioma + nome do CSV)
    youtube/                   #   YouTube Data API (YouTubeApi) e descoberta de vídeos de um canal ou playlist
    legenda/                   #   download da legenda (interface LegendaProvider)
    indexacao/                 #   índice invertido: estimador de tempo e IndexadorDeVideo
    painel/                    #   painel local (perfil "painel"): PainelDeIngestao, PainelController, SoLocalFilter
      sugestoes/               #     sugestões de exemplos: SugestoesRepository (contagem), RankingDeSugestoes, GeradorDeSugestoes
src/main/resources/
  db/migration/                # Flyway (V1 tabelas, V2 canal, V3 tradução, V4 legenda automática, V5 descartados, V6 remove a tradução)
  painel/                      # página do painel de ingestão (só é servida no perfil "painel")
  static/                      # front (HTML/CSS e JS em módulos ES, sem build; fonts/, icons.svg)
    js/                        #   app (orquestra), api, estudio, legenda, traducao, historico, idiomas, player, ferramentas, pronuncia...
data/<idioma>/<canal>.csv      # listas de vídeos da ingestão
infra/                         # produção: compose, Nginx, SQL do papel somente leitura e scripts do índice
docs/                          # identidade visual, roteiro de vídeo e roteiro de operação (operacao.md)
```

---

## Limitações conhecidas

<details open>
<summary><strong>Busca e tempo</strong></summary>

- O YouTube não informa o tempo de cada palavra: o início é **estimado** dentro do trecho (distribuição uniforme). Por isso o vídeo nunca começa depois do início da fala que contém a frase: recua 3 s (2,5 s no loop e na tecla R) e, se o recuo cair dentro dessa fala, começa no começo dela; se cair na fala anterior, obedece o recuo. O loop termina no fim da fala. No modo **Frase** da prática, os dois lados ficam a no máximo 5 s da frase (fala longa); no modo **Trecho**, valem as falas inteiras. Nos trechos mais longos o erro é maior.
- A busca tem dois níveis: **exata** (frase inteira, em sequência, no mesmo vídeo) e **aproximada** (≥ 70% das palavras na ordem, dentro de uma janela de 2× o tamanho da frase; só para frases de 2+ palavras). Máximo de 2 resultados por vídeo e 50 no total. O ranking é simples: exatas, depois mais palavras casadas, depois trecho mais compacto; em empate, legenda manual antes da automática.
- Na busca aproximada, palavras muito comuns geram muitos candidatos; há um teto de 20.000 linhas por busca. Quando as palavras são muito comuns, o corte favorece os vídeos indexados primeiro.
- Acento não é removido (`cafe` ≠ `café`) e `dont` não casa com `don't`.
- Números aparecem como dígitos na legenda mas são falados por extenso.
- A barra da palavra falada é **estimada**: o relógio segue o vídeo, mas o tempo de cada palavra é proporcional ao número de letras dentro da fala, então deriva quando há pausa no meio ou quando a legenda aparece antes da voz. Na legenda automática o fim da fala é cortado no início da seguinte. Marcações como `(Laughter)` também recebem a barra. Com a aba em segundo plano a barra não é desenhada (o loop do trecho continua).
- Chinês e japonês não têm espaço entre palavras: cada caractere é um token, então a busca é por sequência de caracteres. Fora do inglês o apóstrofo separa palavras (`l'homme` vira `l` + `homme`).

</details>

<details open>
<summary><strong>Legenda</strong></summary>

- Vídeos sem legenda manual entram com a **legenda automática** do YouTube (reconhecimento de voz: sem pontuação e com erros ocasionais). A origem fica em `video.legenda_automatica`, a interface mostra o selo "Legenda automática" e, na busca, os vídeos de legenda manual vêm antes. Vídeo sem nenhuma das duas não entra.
- A tradução só funciona no **Chrome ou Edge no desktop** (versão 138 ou mais recente); não existe em celulares, Firefox nem Safari. É tradução automática de modelo local: serve para entender a fala, mas pode errar em expressões e gírias.
- A legenda de um vídeo inteiro vai de uma vez para o navegador (~60 KB para 18 minutos, comprimida pelo servidor); vídeos de horas ficariam pesados.
- A biblioteca de legendas usa uma API não documentada do YouTube e pode parar de funcionar; por isso fica atrás da interface `LegendaProvider`.
- `embeddable=true` não garante que o vídeo toque em todos os países.

</details>

<details open>
<summary><strong>Front e pronúncia</strong></summary>

- O seletor de idioma estilizado (`appearance: base-select`) existe no Chrome e no Edge 135+ e no Safari 27+. No Firefox continua a lista nativa do sistema, que funciona igual.
- A detecção do idioma só considera idiomas **já indexados**; palavra solta que existe em dois deles fica no idioma escolhido.
- A transliteração mostra as **letras**, não a pronúncia exata. Japonês com kanji sai em pinyin (`日本語` vira `rì běn yǔ`); avaliar o Kuromoji quando houver japonês indexado.
- No **Chrome do Android** o reconhecimento continua parando na primeira pausa: lá o `continuous` existe, mas não tem efeito. A junção de falas com pausa vale para computador.
- O reconhecimento de voz é do navegador (no Chrome e no Edge o áudio vai para os servidores do fabricante). A comparação é por palavra: números falados por extenso e sotaques menos comuns podem ser reconhecidos errado.
- Os botões usam ícones em SVG de um sprite local (sem requisições externas), mas as miniaturas da lista vêm do YouTube.
- O volume não passa de 100%: o player do YouTube roda em outro domínio e o navegador não deixa a página amplificar o áudio dele.

</details>
