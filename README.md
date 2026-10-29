# OuvirFrase

Clone do [YouGlish](https://youglish.com) para treinar escuta em inglês: digite uma palavra ou frase e veja trechos de vídeos do YouTube em que ela é realmente falada, com a legenda destacada fora do player.

> Status: **V1 (MVP) funcional localmente.** Deploy em produção ainda não feito.

## Como funciona

1. **Ingestão (offline):** listas curadas de vídeos (`data/<idioma>/<canal>.csv`) são filtradas (só vídeos incorporáveis), a legenda manual em inglês é baixada, normalizada e gravada em um **índice invertido**.
2. **Busca (online):** a frase digitada passa pela mesma normalização e é procurada no índice. O usuário nunca consulta o YouTube: a busca lê só o banco.
3. **Front:** uma página estática que toca o vídeo (YouTube IFrame Player API) 3 segundos antes da frase e destaca a frase na legenda.

### Índice invertido (feito à mão no PostgreSQL)

| Tabela | Papel |
|---|---|
| `video` | um vídeo indexado (`youtube_id` único) |
| `trecho` | cada trecho de legenda (texto, início, duração) |
| `ocorrencia` | **o índice**: uma linha por palavra, com `palavra`, `posicao` e `inicio_seg` |

- `posicao` é o contador de palavras do **vídeo inteiro** (não reinicia a cada trecho), o que permite achar frases que cruzam dois trechos.
- Índice B-tree em `ocorrencia (palavra, video_id, posicao)`.
- **Busca exata:** acha as ocorrências da 1ª palavra e exige as seguintes em `posicao + 1`, `+ 2`… no mesmo vídeo (self-join). Ver `BuscaRepository`.
- A mesma função de normalização (`Normalizador`) é usada no índice e na frase digitada.

## Stack

Java 21 · Spring Boot 4.1 (MVC, Data JPA, Validation, Actuator) · PostgreSQL 16 · Flyway · Maven · Lombok · Apache Commons CSV · [youtube-transcript-api](https://github.com/Thoroldvix/youtube-transcript-api) · JUnit 5 + Mockito + AssertJ + Testcontainers · GitHub Actions · Docker.

## Rodando localmente

Pré-requisitos: JDK 21 e Docker.

```bash
docker compose up -d          # PostgreSQL na porta 5433
./mvnw spring-boot:run        # sobe a aplicação em http://localhost:8080
```

As migrações do Flyway rodam na subida. O banco começa vazio: é preciso rodar a ingestão.

### Ingestão

1. Crie uma chave da **YouTube Data API v3** (Google Cloud) e coloque em `.env` na raiz (o arquivo é ignorado pelo git):
   ```
   YOUTUBE_API_KEY=sua-chave
   ```
2. Os vídeos ficam em `data/<idioma>/<canal>.csv` (por exemplo `data/en/ted.csv`), com as colunas `youtube_id,idioma,titulo`. A coluna `idioma` precisa bater com a pasta; o nome do arquivo vira o canal do vídeo. Linhas com `#` são comentário.
   Para **descobrir** os vídeos de um canal em vez de montar a lista à mão:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=ingestao "-Dspring-boot.run.arguments=--canal=@TED --idioma=en --max=100"
   ```
   Isso só grava/atualiza `data/en/ted.csv` (vídeos com legenda, 60 s ou mais, ainda não conhecidos); revise o arquivo antes de indexar.
3. Indexe:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=ingestao
   ```

- Vídeos já indexados são pulados, então rodar de novo não duplica nada.
- Problema do vídeo (não incorporável, sem legenda manual) → descarta com motivo e segue. Falha de infraestrutura (chave inválida, cota, **bloqueio de IP pelo YouTube**) → para tudo. Basta esperar e rodar de novo.
- O YouTube bloqueia IPs que fazem muitas requisições. Aumente a pausa entre vídeos se necessário: `-Dspring-boot.run.arguments=--ingestao.pausa-ms=6000`.
- `--ingestao.limite=N` processa no máximo N vídeos por execução (o resto aparece como "adiados"). Se o YouTube bloquear o IP no meio, o relatório parcial é impresso antes de sair com erro.
- Ao final é impresso um relatório: quantos entraram, quantos já existiam e quantos foram descartados (e por quê).

### Idiomas

Uma pasta por idioma em `data/`: `en`, `es`, `fr`, `de`, `it`, `ja`, `ko`, `zh-CN`, `ru`. O nome da pasta (e a coluna `idioma` do CSV) é o **código que o YouTube usa nas legendas** (por exemplo `zh-CN`, `pt-BR`), porque é com ele que a legenda manual é procurada. Para um idioma novo, crie a pasta e o CSV; o seletor do front aparece sozinho quando há mais de um idioma indexado.

### API

`GET /api/busca?frase=...&idioma=en` (`idioma` é opcional, padrão `en`). `GET /api/idiomas` lista os idiomas que têm vídeos indexados.

```json
{ "resultados": [
  { "youtubeId": "abc123XYZ_-", "titulo": "…", "inicioSeg": 12.4, "legenda": "texto do trecho",
    "pontuacao": 1.0, "exata": true }
] }
```

`inicioSeg` já vem com 3 s de antecedência. As frases exatas vêm primeiro (`exata: true`); depois as aproximadas, com `pontuacao` = palavras casadas / total (mínimo 0,7). Sem resultado: `resultados` vazio. Frase vazia ou com mais de 200 caracteres: `400`.

## Testes

```bash
./mvnw verify
```

Testes unitários (Normalizador, estimador de tempo, leitor de CSV, filtro, provider de legenda, serviço de ingestão) e de integração com Testcontainers (indexação e busca). Precisa do Docker. O CI roda tudo a cada push/PR para `main` e `develop`.

## Estrutura

```
src/main/java/com/lucasmanoel/ouvirfrase/
  config/  controller/  service/  repository/  model/  dto/  exception/
  ingestao/   # pipeline offline: CSV, filtro embeddable, legendas, runner
src/main/resources/static/   # front (HTML/CSS/JS puro)
```

## Limitações conhecidas

- O YouTube não informa o tempo de cada palavra: o início é **estimado** dentro do trecho (distribuição uniforme). O recuo de 3 s cobre a imprecisão. Nos trechos mais longos o erro é maior.
- Só legendas **manuais** em inglês; vídeo sem legenda manual não entra.
- A busca tem dois níveis: **exata** (frase inteira, em sequência, no mesmo vídeo) e **aproximada** (≥ 70% das palavras na ordem, dentro de uma janela de 2× o tamanho da frase; só para frases de 2+ palavras). Máximo de 2 resultados por vídeo e 50 no total. O ranking é simples: exatas, depois mais palavras casadas, depois trecho mais compacto.
- Na busca aproximada, palavras muito comuns geram muitos candidatos; há um teto de 20.000 linhas por busca.
- Acento não é removido (`cafe` ≠ `café`) e `dont` não casa com `don't`.
- Números aparecem como dígitos na legenda mas são falados por extenso.
- Chinês e japonês não têm espaço entre palavras: cada caractere é um token, então a busca é por sequência de caracteres. Fora do inglês o apóstrofo separa palavras (`l'homme` vira `l` + `homme`).
- A biblioteca de legendas usa uma API não documentada do YouTube e pode parar de funcionar; por isso fica atrás da interface `LegendaProvider`.
- `embeddable=true` não garante que o vídeo toque em todos os países.

## Próximos passos (pós-MVP)

Automação da seleção de vídeos · front mais elaborado · pronúncia, quiz, flashcards · deploy.
