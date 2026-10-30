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
| Digite uma palavra ou frase em qualquer idioma indexado. | O vídeo toca 3 s antes da frase, com loop e velocidade de 0,25× a 1,5×. | Fale no microfone e veja, palavra por palavra, o que o navegador entendeu. |

---

## Como funciona

```
CSV de vídeos → filtro embeddable → legenda → índice invertido (PostgreSQL) → busca → front
└───────────── ingestão (offline) ──────────────────────────────┘   └── online ──┘
```

1. **Ingestão (offline).** Listas curadas de vídeos (`data/<idioma>/<canal>.csv`) são filtradas (só vídeos incorporáveis). A legenda (manual, ou a automática quando não há manual) é baixada, normalizada e gravada em um **índice invertido**.
2. **Busca (online).** A frase digitada passa pela mesma normalização e é procurada no índice. O usuário nunca consulta o YouTube: a busca lê só o banco.
3. **Front.** Uma página estática que toca o vídeo (YouTube IFrame Player API) 3 segundos antes da frase e destaca a frase na legenda.

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

---

## Ingestão

A ingestão roda **offline**, num perfil à parte (`ingestao`), e nunca por endpoint. O site em si não consulta o YouTube.

### 1. Preparar

**Chave da API.** Crie uma chave da **YouTube Data API v3** (Google Cloud) e coloque em `.env` na raiz. O arquivo é ignorado pelo git e só é lido no perfil `ingestao`; a variável de ambiente `YOUTUBE_API_KEY` também serve.

```
YOUTUBE_API_KEY=sua-chave
```

**Lista de vídeos.** Os vídeos ficam em `data/<idioma>/<canal>.csv` (por exemplo `data/en/ted.csv`), com as colunas `youtube_id,idioma,titulo`. A coluna `idioma` precisa bater com a pasta; o nome do arquivo vira o canal do vídeo. Linhas com `#` são comentário.

Para **descobrir** os vídeos de um canal em vez de montar a lista à mão:

```bash
./mvnw "-Dspring-boot.run.profiles=ingestao" "-Dspring-boot.run.arguments=--canal=@TED --idioma=en --max=100" spring-boot:run
```

| Parâmetro | O que faz |
|---|---|
| `--canal` | `@handle`, link do canal (`youtube.com/@handle`) ou id do canal (`UC…`). |
| `--idioma` | Idioma dos vídeos e pasta de destino. Padrão: `en`. |
| `--max` | Máximo de vídeos novos a gravar. Padrão: `100`. |

Isso só grava ou atualiza o CSV (`data/en/ted.csv`, no exemplo) com vídeos que têm legenda (manual ou automática), duram 60 s ou mais e ainda não são conhecidos. **Revise o arquivo antes de indexar.**

### 2. Indexar

O comando abaixo funciona no PowerShell e no bash. **Mantenha as aspas:** no PowerShell, sem elas o `-D…=` quebra no ponto.

```bash
./mvnw "-Dspring-boot.run.profiles=ingestao" "-Dspring-boot.run.arguments=--ingestao.limite=15 --ingestao.pausa-ms=6000" spring-boot:run
```

| Parâmetro | O que faz | Padrão |
|---|---|---|
| `-Dspring-boot.run.profiles=ingestao` | Ativa o perfil de ingestão (sem servidor web). Sem ele, o comando sobe o site. | — |
| `-Dspring-boot.run.arguments="…"` | Repassa os argumentos abaixo para a aplicação. | — |
| `--ingestao.limite=N` | Processa no máximo N vídeos por execução. O resto aparece como "adiados" no relatório; rode de novo para continuar. Só vídeos novos contam no limite; descartados em rodadas anteriores não. `0` = sem limite. | `0` |
| `--ingestao.pausa-ms=N` | Pausa entre vídeos, em milissegundos. Pausa maior reduz o risco de bloqueio do IP; `6000` é o valor usado nos lotes. | `1500` |
| `--ingestao.dados=pasta` | Pasta raiz dos CSVs. | `data` |

> [!WARNING]
> **Rode um lote por vez e espere entre eles.** O YouTube bloqueia IPs que fazem muitas requisições (erro `429`). Nos testes, uma segunda execução poucos minutos depois de um lote de 15 vídeos já recebeu `429`. Se acontecer, espere **horas**, não minutos.

### Como a ingestão se comporta

- **Vídeos já indexados são pulados.** Rodar de novo não duplica nada.
- **Problema do vídeo** (não encontrado, não incorporável, sem legenda manual nem automática, sem palavras aproveitáveis) → descarta com motivo e segue. O descarte é gravado em `video_descartado`: nas próximas rodadas o vídeo é pulado sem chamar o YouTube e sem gastar o `--ingestao.limite`. Para reavaliar um vídeo, apague a linha dele: `DELETE FROM video_descartado WHERE motivo = 'SEM_LEGENDA';` (ou `youtube_id = '...'`).
- **Falha de infraestrutura** (chave inválida, cota, **bloqueio de IP pelo YouTube**) → para tudo, imprime o relatório parcial e sai com erro (código 1). Basta esperar e rodar de novo.
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
{ "resultados": [
  { "youtubeId": "abc123XYZ_-", "titulo": "…", "inicioSeg": 12.4, "fimSeg": 18.0, "legenda": "texto do trecho",
    "pontuacao": 1.0, "exata": true, "legendaAutomatica": false }
] }
```

- `inicioSeg` já vem com 3 s de antecedência; `fimSeg` é onde termina o último trecho da legenda (o front usa para o loop).
- As frases exatas vêm primeiro (`exata: true`); depois as aproximadas, com `pontuacao` = palavras casadas / total (mínimo 0,7).
- `legendaAutomatica` indica a origem da legenda. Na busca, os vídeos de legenda manual vêm antes.
- Sem resultado: `resultados` vazio. Frase vazia ou com mais de 200 caracteres: `400`.

### Legenda completa do vídeo

`GET /api/videos/{youtubeId}/legenda` devolve a legenda completa do vídeo, em ordem, para o front acompanhar o vídeo (com cache de 1 dia no navegador):

```json
[ { "texto": "so what do you think", "inicioSeg": 12.4, "duracaoSeg": 2.1 } ]
```

Vídeo desconhecido: lista vazia.

---

## Front

Página estática (HTML, CSS e JS puro, sem build) servida pelo próprio Spring. Identidade visual, cores, tipografia e voz em [`docs/identidade-visual.md`](docs/identidade-visual.md).

- **Tela inicial:** busca, seletor de idioma, exemplos, buscas recentes (guardadas só no navegador) e o ritmo "Busque. Ouça. Repita.".
- **Estúdio (depois da busca):** player com barra de progresso do trecho, a dock de controles, a legenda com a frase destacada, a faixa de prática e a lista de todas as ocorrências (miniatura, trecho, selo de frase aproximada). No desktop tudo cabe numa tela sem rolar; no celular os controles ficam acima da dobra.
- **Legenda que acompanha o vídeo:** o cartão mostra a fala do instante atual (também com o vídeo pausado ou depois de arrastar a barra do YouTube), com a frase buscada destacada. Se a API da legenda falhar, o cartão continua mostrando o trecho do resultado.
- **Selo "Legenda automática":** aparece no estúdio quando o trecho vem de legenda gerada por reconhecimento de voz, que pode ter erros.
- **Dock do player:** play/pause, voltar e avançar 2 s, loop do trecho (volta ao início ao chegar em `fimSeg`) e velocidade de 0,25× a 1,5× (lembrada no navegador).
- **Pronúncia:** o botão de microfone fica sempre visível. O usuário repete a frase buscada (ou o trecho inteiro); o navegador reconhece a fala (Web Speech API) e a página compara palavra por palavra, com porcentagem e as palavras que faltaram. Não há nada no servidor. Só funciona no Chrome e no Edge e exige HTTPS fora do `localhost`.
- **Idioma:** a pílula de idioma fica sempre visível; vira um botão (com seta) quando há mais de um idioma indexado.
- **Link da busca:** `?q=frase&l=idioma`; o botão de link copia o endereço da busca atual.

**Créditos.** Ícones: [Phosphor Icons](https://phosphoricons.com) (MIT). Fontes auto-hospedadas: [Inter](https://rsms.me/inter/) e [Lora](https://github.com/cyrealtype/Lora-Cyrillic) (SIL OFL 1.1), via pacotes `@fontsource`. Miniaturas dos vídeos vêm de `i.ytimg.com`.

---

## Testes

```bash
./mvnw verify
```

Testes unitários (Normalizador, estimador de tempo, leitor de CSV, filtro, provider de legenda, serviço de ingestão) e de integração com Testcontainers (indexação e busca). Precisa do Docker. O CI roda tudo a cada push/PR para `main` e `develop`.

## Estrutura

```
src/main/java/com/lucasmanoel/ouvirfrase/
  config/  controller/  service/  repository/  model/  dto/  exception/
  ingestao/                    # pipeline offline: CSV, filtro embeddable, legendas, runner
src/main/resources/
  db/migration/                # Flyway (V1 tabelas, V2 canal, V3 tradução, V4 legenda automática, V5 descartados, V6 remove a tradução)
  static/                      # front (HTML/CSS/JS puro: app, ferramentas, pronuncia; fonts/, icons.svg)
data/<idioma>/<canal>.csv      # listas de vídeos da ingestão
docs/                          # identidade visual e roteiro de vídeo
```

---

## Limitações conhecidas

<details open>
<summary><strong>Busca e tempo</strong></summary>

- O YouTube não informa o tempo de cada palavra: o início é **estimado** dentro do trecho (distribuição uniforme). O recuo de 3 s cobre a imprecisão. Nos trechos mais longos o erro é maior.
- A busca tem dois níveis: **exata** (frase inteira, em sequência, no mesmo vídeo) e **aproximada** (≥ 70% das palavras na ordem, dentro de uma janela de 2× o tamanho da frase; só para frases de 2+ palavras). Máximo de 2 resultados por vídeo e 50 no total. O ranking é simples: exatas, depois mais palavras casadas, depois trecho mais compacto; em empate, legenda manual antes da automática.
- Na busca aproximada, palavras muito comuns geram muitos candidatos; há um teto de 20.000 linhas por busca.
- Acento não é removido (`cafe` ≠ `café`) e `dont` não casa com `don't`.
- Números aparecem como dígitos na legenda mas são falados por extenso.
- Chinês e japonês não têm espaço entre palavras: cada caractere é um token, então a busca é por sequência de caracteres. Fora do inglês o apóstrofo separa palavras (`l'homme` vira `l` + `homme`).

</details>

<details open>
<summary><strong>Legenda</strong></summary>

- Vídeos sem legenda manual entram com a **legenda automática** do YouTube (reconhecimento de voz: sem pontuação e com erros ocasionais). A origem fica em `video.legenda_automatica`, a interface mostra o selo "Legenda automática" e, na busca, os vídeos de legenda manual vêm antes. Vídeo sem nenhuma das duas não entra.
- A legenda de um vídeo inteiro vai de uma vez para o navegador (~60 KB para 18 minutos, comprimida pelo servidor); vídeos de horas ficariam pesados.
- A biblioteca de legendas usa uma API não documentada do YouTube e pode parar de funcionar; por isso fica atrás da interface `LegendaProvider`.
- `embeddable=true` não garante que o vídeo toque em todos os países.

</details>

<details open>
<summary><strong>Front e pronúncia</strong></summary>

- O reconhecimento de voz é do navegador (no Chrome e no Edge o áudio vai para os servidores do fabricante). A comparação é por palavra: números falados por extenso e sotaques menos comuns podem ser reconhecidos errado.
- Os botões usam ícones em SVG de um sprite local (sem requisições externas), mas as miniaturas da lista vêm do YouTube.
- O volume não passa de 100%: o player do YouTube roda em outro domínio e o navegador não deixa a página amplificar o áudio dele.

</details>

## Próximos passos (pós-MVP)

Quiz e flashcards (exigem login) · deploy.
