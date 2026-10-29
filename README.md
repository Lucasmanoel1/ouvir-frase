# OuvirFrase

Clone do [YouGlish](https://youglish.com) para treinar escuta em inglês: digite uma palavra ou frase e veja trechos de vídeos do YouTube em que ela é realmente falada, com a legenda destacada fora do player.

> Status: **V1 (MVP) funcional localmente.** Deploy em produção ainda não feito.

## Como funciona

1. **Ingestão (offline):** uma lista curada de vídeos (`data/videos.csv`) é filtrada (só vídeos incorporáveis), a legenda manual em inglês é baixada, normalizada e gravada em um **índice invertido**.
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
2. Edite `data/videos.csv` (`youtube_id,idioma,titulo`; linhas com `#` são comentário).
3. Rode:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=ingestao
   ```

- Vídeos já indexados são pulados, então rodar de novo não duplica nada.
- Problema do vídeo (não incorporável, sem legenda manual) → descarta com motivo e segue. Falha de infraestrutura (chave inválida, cota, **bloqueio de IP pelo YouTube**) → para tudo. Basta esperar e rodar de novo.
- O YouTube bloqueia IPs que fazem muitas requisições. Aumente a pausa entre vídeos se necessário: `-Dspring-boot.run.arguments=--ingestao.pausa-ms=6000`.
- Ao final é impresso um relatório: quantos entraram, quantos já existiam e quantos foram descartados (e por quê).

### API

`GET /api/busca?frase=...`

```json
{ "resultados": [
  { "youtubeId": "abc123XYZ_-", "titulo": "…", "inicioSeg": 12.4, "legenda": "texto do trecho" }
] }
```

`inicioSeg` já vem com 3 s de antecedência. Sem resultado: `resultados` vazio. Frase vazia ou com mais de 200 caracteres: `400`.

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
- Busca **exata**: a frase inteira, em sequência, no mesmo vídeo. Sem busca aproximada nem ranking; os resultados saem na ordem do banco (limite de 50).
- Acento não é removido (`cafe` ≠ `café`) e `dont` não casa com `don't`.
- Números aparecem como dígitos na legenda mas são falados por extenso.
- A biblioteca de legendas usa uma API não documentada do YouTube e pode parar de funcionar; por isso fica atrás da interface `LegendaProvider`.
- `embeddable=true` não garante que o vídeo toque em todos os países.

## Próximos passos (pós-MVP)

Busca por lacunas e ranking · automação da seleção de vídeos · outros idiomas · front mais elaborado · pronúncia, quiz, flashcards · deploy.
