# PRD: lote 2 do OuvirFrase (gerador de exemplos e recuo do trecho)

> Fase 1 de 3 (Pesquisa → Spec → Code). Este documento diz **o quê** e **por quê**, com a abordagem recomendada e o que foi descartado. A spec (fase 2) transforma isto em arquivos, assinaturas e testes.
> Base: código da pasta em 04/11/2026 (branch `feature/legenda-karaoke`, com as 4 branches do lote 1 commitadas), `OuvirFrase-contexto-legivel.txt` e a nota de passagem de 04/11.
> Versão 1 (04/11/2026), já com duas respostas do Lucas (seção 6). Não há decisão pendente; a spec pode começar.

---

## 1. Princípios (valem para todas as fases)
Os 5 erros da seção 3 do contexto: overengineering, repetir código, não saber fazer, reinventar a roda, juntar tudo no mesmo lugar. A seção 5 confere este PRD contra eles.

Regras que continuam: sem commit/push (só o Lucas); ícones Phosphor no sprite, sem emojis; voz pt-BR com "você"; respeitar `prefers-reduced-motion`; testes com boa cobertura, sem redundância; avisar antes de implementar algo não pedido; **nunca inventar frases de exemplo**.

---

## 2. Requisitos

### R1. Gerador de sugestões de exemplos no painel
**Hoje:** o Lucas roda à mão, no `psql`, uma consulta que monta pares e trios de palavras seguidas (`lead` sobre `ocorrencia`), conta vezes e vídeos, corta por `vídeos >= 3`, `vezes >= 10`, `length >= 9` e ordena por vídeos. Copia o resultado e manda para o Claude escolher as frases de `exemplos.js` (hoje `en` 27, `es` 26, `ru` 25). Problemas da consulta atual, medidos na seção 4:
- **Ordena por quantidade.** O topo é lixo gramatical: `have been`, `the first`, `one of the`, `through the`, `there was`.
- **Devolve pedaços.** `a lot` aparece junto com `a lot of`; `in front` com `in front of`; `as well` e `well as` com `as well as`.
- **Atravessa trechos.** O `lead` não olha `trecho_id`, então junta o fim de uma frase com o começo da seguinte (`... thank you. So ...` vira `you so`).
- **Não vê canal.** O bordão de um canal (abertura, encerramento, "inscreva-se") está em todos os vídeos dele e sobe no ranking.
- `length >= 9` foi um remendo para o inglês: corta `no sé`, `a veces`, `por eso`.

**Requisito**
- Seção nova no painel, "Sugestões de exemplos": **um botão por idioma indexado**. O clique gera candidatas de 2 a 4 palavras, **ranqueadas por qualidade**, e mostra a lista (frase, vídeos, vezes).
- Botão **Copiar** que põe a lista na área de transferência, uma frase por linha (`frase · N vídeos`), pronta para colar no chat.
- Campo "Mínimo de vídeos" com padrão **6** (o russo tem poucos vídeos e hoje as frases dele estão em 3 a 6; o Lucas baixa o número quando precisar).
- Frases que já estão em `exemplos.js` não entram na lista nem na cópia; a seção diz quantas foram puladas.
- Toda sugestão sai do próprio índice, então a busca exata acha cada uma (a busca exata casa por `posicao` consecutiva, que vale dentro do trecho).
- Conta o **índice inteiro** do idioma (decisão do Lucas, seção 6), aceitando que o botão fica mais lento conforme o índice cresce.

**Abordagem recomendada: contagem no SQL, ranking em Java**

1. **Contagem (SQL, uma consulta).** Mesma ideia da consulta atual, com três mudanças:
   - n-gramas de 1 a 5 palavras (1 para o cálculo de associação, 5 só para julgar se a de 4 é pedaço);
   - só dentro do mesmo trecho: `lead(trecho_id, k) = trecho_id` (sai o "atravessa trechos" e o lixo de fronteira de frase);
   - agrupa por vídeo e canal antes de agrupar por frase, e devolve `vezes`, `videos` e `canais`, com `HAVING videos >= :minimo`. As palavras soltas vêm todas (são poucas).
   O total de palavras do idioma (`N`) vem de um `count(*)` simples.
2. **Ranking (Java, classe pura e testável).** Recebe as contagens e devolve as candidatas:
   - **Filtros:** 2 a 4 palavras; `videos >= mínimo`; `canais >= 2` (ou o total de canais do idioma, se ele só tiver um) para matar bordão de canal.
   - **Não é pedaço:** descarta a frase se uma única palavra a completa em 50% ou mais das vezes, à direita ou à esquerda (`a lot` é seguido de `of` em 64% das vezes: pedaço; `you know` só às vezes é seguido de `what`, então fica). É a versão simples da "entropia de ramificação" (seção 3): usa os mesmos números, sem calcular entropia.
   - **Ordem:** média de dois postos: (a) espalhamento (número de vídeos) e (b) **associação** (NPMI, a informação mútua normalizada, no ponto mais fraco da frase: para `i don't know`, o menor valor entre `i | don't know` e `i don't | know`). O primeiro garante que a frase é comum; o segundo, que as palavras andam juntas e não por acaso (`of the` tem muito vídeo e pouca associação, então afunda).
   - Devolve as 150 primeiras.
3. **Endpoint:** `GET /api/painel/sugestoes?idioma=en&minimo=6` no `PainelController` (já tem a validação de idioma). Os botões vêm de `GET /api/idiomas`, que já existe e responde também no painel.
4. **Página:** seção nova em `painel/index.html`; `painel.js` importa `EXEMPLOS` de `js/exemplos.js` (o painel já serve o `static/`), então a regra "já está nos exemplos" não duplica dados. Copiar com `navigator.clipboard.writeText` (o painel roda em `localhost`, que conta como contexto seguro). Botão desabilitado com "Gerando…" enquanto a consulta roda. Ícone `copy` copiado do Phosphor para o sprite.
5. Chinês e japonês (um caractere por token): mostrar a frase sem espaço entre os caracteres. Não estão indexados; só não pode quebrar.
6. Atualizar o comentário de `exemplos.js` (hoje aponta para "a consulta da seção 7 de docs/spec.md") para apontar para o painel.

**Desempenho (medido, seção 4).** Cresce linear com o índice: ~17 s por milhão de palavras num contêiner de 2 vCPU (a consulta atual, mais pobre, leva ~8 s). Com 5 milhões passou de 100 s. Hoje o `en` tem ~400 vídeos (estimativa: perto de 1 milhão de palavras); o `es`, com ~1.300 vídeos listados, deve ficar perto de 3 milhões depois da carga, ou seja, perto de 1 minuto.
- Para o protótipo: rodar a consulta no clique, síncrona, com "Gerando…" na tela.
- **Se passar de ~1 min**, há uma otimização conhecida e medida (corta pela metade): poda por pares (o princípio "Apriori": uma frase só chega a 6 vídeos se o par das duas primeiras palavras chega). Fica registrada; não entra agora.
- `work_mem` maior não ajudou (medido: 512 MB foi igual a 64 MB).

**Descartado**
- **Lista de stopwords nas bordas** (rejeitar frase que começa ou termina em `the`, `of`, `to`...; listas prontas como stopwords-iso ou as do Lucene). Mata justamente o que o aprendiz precisa: `a lot of`, `in front of`, `think about`, `be able to`, `talk about` (todas já em `exemplos.js`), `por qué` e `потому что` (as duas palavras são stopwords). Também testei a versão "rejeitar frase feita só das 30 palavras mais comuns do idioma" (lista tirada do próprio índice): o resultado ficou quase igual ao sem ela, então não paga a regra a mais.
- **Log-likelihood (Dunning) e t-score:** favorecem frequência, que o posto de espalhamento já cobre. Seria um terceiro número para o mesmo efeito.
- **Entropia de ramificação completa:** pede a distribuição inteira das palavras vizinhas; a razão da vizinha mais comum usa os mesmos dados e decidiu bem os casos testados.
- **PMI/NPMI sozinho:** o topo vira nome próprio (`los angeles`, `hong kong`, `puerto rico`). Útil como um dos dois postos, ruim sozinho.
- **Ordenar só por vídeos com os filtros:** o topo continua `in the`, `of the`, `to the`.
- **Amostra aleatória de 300 vídeos por clique** (tempo fixo de ~10 s): o Lucas preferiu o índice inteiro (lista estável).
- **Contar em Java** (ler as ocorrências em ordem e contar num `HashMap`): milhões de chaves no heap; o Postgres já agrupa e despeja em disco quando precisa.
- **Tabela de frases mantida na ingestão:** mais uma tabela, migração e código no caminho crítico da ingestão para um botão usado de vez em quando.
- **Contar só legenda manual:** o `es` e o `ru` são quase só automáticos. Erro de legenda automática raramente se repete em 6 vídeos de 2 canais.

**Aceite**
- O painel mostra um botão para cada idioma indexado (`en`, `es`, `ru`); clicar em `en` mostra até 150 frases de 2 a 4 palavras e o total pulado por já estar em `exemplos.js`.
- No topo 50 de `en`: nenhum pedaço cuja forma longa também esteja na lista (`a lot` sem `of`, `in front` sem `of`, `as well` sem `as`); `of the` e `in the` fora do topo 100.
- Nenhuma frase atravessa trecho; nenhuma está em menos vídeos do que o mínimo pedido; nenhuma vem de um canal só.
- **Copiar** põe a lista na área de transferência, uma por linha; buscar qualquer frase da lista no site dá resultado exato.
- Com mínimo 3, o `ru` devolve candidatas.

---

### R2. Recuo do início do trecho (2 s na primeira vez, menos no loop)
**Hoje**
- O backend recua 3 s do início **estimado** da primeira palavra (`BuscaService.RECUO_SEG = 3.0`), e o front tem a mesma constante (`legenda.js`, `RECUO_SEG = 3`) só para desfazer a conta e saber se uma fala pertence ao trecho do resultado (marca das palavras soltas da busca aproximada).
- O loop (`ferramentas.js`) volta para o mesmo `inicioSeg` e vai até `fimSeg`, que é o **fim da última fala** da legenda. Se a frase está no começo de uma fala de 5 s, o loop toca uns 4 s de sobra antes de recomeçar e mais 3 s de recuo depois. É a demora que o Lucas sente.
- O primeiro vídeo da sessão começa ainda mais cedo: `player.js` cria o player com `start: Math.floor(inicioSeg)`, porque o parâmetro `start` só aceita inteiro. Até 1 s a mais de recuo, só no primeiro vídeo. Os seguintes usam `loadVideoById`, que aceita fração.
- O tempo da palavra é estimado (`EstimadorDeTempo`: posição dentro do trecho dividida igualmente). O recuo de 3 s existia para cobrir esse erro.

**Requisito**
- Primeira reprodução: começar no máximo **2 s** antes do início estimado da frase.
- Loop (e a tecla `R`): recomeçar mais perto, **1 s** antes.
- Fim do loop: no fim estimado da frase **mais ~1 s**, sem passar do fim da fala (decisão do Lucas, seção 6).
- Nenhuma constante de recuo duplicada entre backend e front.

**Abordagem recomendada: o backend devolve fatos, o front decide as folgas**
- O recuo é decisão de tela (quanto contexto mostrar antes, e o loop quer menos que a primeira vez). Então sai do backend: `BuscaService` perde `RECUO_SEG` e a API devolve só tempos do vídeo:
  - `inicioSeg`: início estimado da primeira palavra (sem recuo);
  - início estimado da **última** palavra (nome na spec);
  - início e fim da **fala** (trecho da legenda) que contém a primeira e a última palavra. O fim da fala é o `fimSeg` de hoje, então só muda de nome; o início da fala é o `inicio_seg` do `t1` que a busca exata já junta, e na aproximada sai do trecho da primeira candidata (mesma consulta de `fimDoTrecho`, devolvendo os dois limites).
- O front calcula num lugar só (função pura, com `node:test`), com as constantes juntas:
  - `início = max(início da fala − 0,5 s, início estimado − recuo)`, com `recuo = 2 s` na primeira vez e `1 s` no loop.
  - `fim do loop = min(fim da fala, início estimado da última palavra + 1,5 s)` (a última palavra dura uns 0,3 a 0,6 s; sobra ~1 s).
  - Por que o `max` com o início da fala: a frase não começa antes da fala que a contém (o tempo da fala é o real da legenda; o da palavra é estimado). Quando a frase abre a fala, o recuo cai de 2 s para 0,5 s **sem risco nenhum**, porque o `max` só atrasa o início, nunca o adianta além do que o recuo fixo já faria.
- `legenda.js` deixa de ter `RECUO_SEG`: a regra das palavras soltas passa a usar o início e o fim da fala que já vêm na resposta.
- `player.js`: um caminho só para tocar. Criar o player sem `start` e, no `onReady`, chamar `loadVideoById({ videoId, startSeconds })` (aceita fração, segundo a documentação da IFrame API). Some o `Math.floor` e o segundo de sobra no primeiro vídeo.
- README: atualizar o contrato da API e a frase "o recuo de 3 s cobre a imprecisão". Os testes de `BuscaServiceTest`, `BuscaAproximadaTest` e `BuscaControllerTest` que conferem `inicioSeg` e `fimSeg` passam a esperar o início sem recuo e os campos novos.

**Risco aceito:** com 1 s no loop, se a estimativa da palavra atrasar mais de 1 s (fala longa com pausa no meio), o loop corta o começo da frase. O `max` não piora isso. Se incomodar no uso, subir o recuo do loop para 1,5 s (uma constante).

**Descartado**
- **Só trocar 3 por 2 nos dois lugares:** mantém a duplicação e o loop continua demorando no fim.
- **Começar sempre no início da fala:** seguro e simples, mas uma fala longa com a frase no fim daria mais de 2 s de espera.
- **Backend devolver os dois inícios prontos** (primeira vez e loop): põe regra de tela no backend e ainda deixaria `legenda.js` dependente dela.
- **Melhorar a estimativa do tempo da palavra agora** (achado A1): pede recalcular `ocorrencia.inicio_seg` do índice inteiro.

**Aceite**
- Buscar uma frase que abre uma fala: o vídeo começa cerca de 0,5 s antes da fala.
- Buscar uma frase no meio de uma fala longa: começa no máximo 2 s antes do início estimado.
- Com o loop ligado: recomeça no máximo 1 s antes e não toca mais de ~1,5 s depois da última palavra; a tecla `R` volta ao mesmo ponto do loop.
- O primeiro vídeo da sessão começa no mesmo ponto que os seguintes (sem o segundo a mais).
- `RECUO` não aparece mais em Java nem em `legenda.js`; as constantes de folga estão num lugar só no front.
- A marca das palavras soltas da busca aproximada continua só dentro do trecho do resultado.

---

## 3. Achados no código (fora dos pedidos)
- **A1. Duas estimativas de tempo diferentes.** O backend estima o início de cada palavra dividindo o trecho por igual (`EstimadorDeTempo`); o karaokê do front estima pelo número de letras (`palavras.js`, `indiceFalado`). É a estimativa pior que decide onde o vídeo começa. Trocar o `EstimadorDeTempo` por letras é uma linha, mas só vale para vídeos novos; o índice atual pede recalcular `ocorrencia.inicio_seg` a partir de `trecho.texto` (sem YouTube). Não entra neste lote; candidato junto com o PRD do tempo real por palavra (`json3`).
- **A2. `start` inteiro no primeiro vídeo** (`player.js`, `Math.floor`): resolvido dentro do R2.
- **A3. Comentário velho em `exemplos.js`** apontando para a consulta da spec do lote 1: resolvido dentro do R1.

---

## 4. Medições (base das decisões do R1)
**Ambiente:** Postgres 16 num contêiner de 2 vCPU e 7 GB, schema igual ao da V1 (mesmos índices), `work_mem` 64 MB. Corpus de teste: Brown (inglês, 500 textos, ~1 milhão de palavras, 15 categorias fazendo papel de canal; cada frase do texto vira um trecho). O índice real do Lucas não estava acessível daqui: os números de qualidade são indicativos e o aceite confere no índice real.

| Consulta | 1 milhão de palavras | 5 milhões |
|---|---|---|
| Atual (2 e 3 palavras, atravessa trechos) | 8 s | 23 s |
| Proposta (1 a 5 palavras, mesmo trecho, por vídeo e canal) | 17 s | > 100 s (interrompida) |
| Proposta com poda por pares (Apriori) | 14 s | 62 s |
| Proposta com amostra de 300 vídeos | ~10 s | ~10 s |

(O corpus de 5 milhões é o Brown repetido 5 vezes com as frases embaralhadas; a repetição faz mais frases passarem do mínimo, então o tempo da última etapa é pessimista.)

**Qualidade (topo de cada ordenação, corpus Brown, mínimo 6 vídeos):**
- Consulta atual: `have been, the first, the other, more than, one of the, through the, there was, they were, of course, about the`.
- Só NPMI: `los angeles, hong kong, puerto rico, viet nam, san francisco, rhode island`.
- Só vídeos, com filtros: `the same, have been, the first, out of, into the, would be`.
- **Recomendada** (média dos postos de vídeos e NPMI, sem pedaços), alguns do topo 60: `has been, more than, at least, rather than, as well as, years ago, no longer, at the same time, for example, too much, each other, even though, i think, based on, according to`.
- Pedaços descartados pela regra dos 50%: `a lot` (64%), `in front` (88%), `as well` (78%), `well as` (98%), `in order` (79%), `at the same` (84%). Falso positivo aceito: `united states` sai porque `the united states` aparece em 86% das vezes; a forma longa fica na lista.
- `of the` ficou na posição 255 e `in the` na 361 (fora do topo 100).

---

## 5. Conferência dos 5 erros
1. **Overengineering:** sem tabela nova, sem migração, sem biblioteca, sem tarefa em segundo plano para o gerador. Dois números (vídeos e NPMI) e uma razão (pedaço); entropia completa, log-likelihood e listas de stopwords ficaram de fora porque não mudaram o resultado ou pioraram. A poda Apriori só entra se o tempo pedir. No R2, uma função pura e três constantes.
2. **Repetir código:** o gerador usa `GET /api/idiomas` (já existe), a validação de idioma do `PainelController` e o `EXEMPLOS` de `exemplos.js` (importado, não copiado). O R2 apaga a constante duplicada e junta os dois caminhos do `player.js` num só.
3. **Não saber fazer:** a medida de associação e a de espalhamento seguem a literatura de expressões formulaicas para ensino (a Academic Formulas List ranqueia por MI e frequência, com MI pesando mais, e exige presença em vários subcorpora); NPMI de Bouma; os tipos de `start` e `startSeconds` vêm da documentação da IFrame API.
4. **Reinventar a roda:** a contagem fica com o Postgres (janela e agregação, que ele faz e despeja em disco); NPMI é a fórmula padrão (a mesma que o Gensim Phrases usa para juntar palavras). Não existe biblioteca Java consolidada e leve para extrair expressões; o Gensim é Python e faria só a parte de pares.
5. **Juntar tudo no mesmo lugar:** a consulta (repositório), o ranking (classe pura) e o endpoint ficam separados, em pacote próprio do painel; no R2 o backend fica com os tempos e o front com as folgas.

---

## 6. Decisões do Lucas (04/11/2026)
1. **Fim do loop:** entra no lote, junto com o R2.
2. **Gerador:** contar o índice inteiro (não amostra), aceitando que fica mais lento com o tempo.

## 7. Entregas sugeridas
| Ordem | Branch | Itens |
|---|---|---|
| 1 | `feature/recuo-trecho` | R2, A2 |
| 2 | `feature/sugestoes-exemplos` | R1, A3 |

O R2 primeiro: é menor e mexe no que o usuário sente. O R1 só é usado pelo Lucas.

## 8. Fora deste lote
Tempo real por palavra (`json3`) e recalcular a estimativa do índice (A1); quiz "Ditado com lacunas" (próximo PRD); deploy.

---

## 9. Fontes
- Academic Formulas List (ranking por MI e frequência; presença em vários subcorpora): Simpson-Vidal e Ellis, https://public.websites.umich.edu/~ncellis/NickEllis/Publications_files/AFL_paper_AppLinxPrepub.pdf e a lista em https://www.eapfoundation.com/vocab/academic/afl/
- NPMI e extração de colocações: https://en.wikipedia.org/wiki/Collocation_extraction e https://www.wikipedia.org/wiki/Pointwise_Mutual_Information
- Gensim Phrases (pontuação NPMI para juntar palavras): https://github.com/piskvorky/gensim/blob/4.1.2/gensim/models/phrases.py
- Entropia de ramificação: Jin e Tanaka-Ishii, https://aclanthology.org/P06-2056/
- Log-likelihood (Dunning, 1993): https://aclanthology.org/J93-1003/
- Listas de stopwords prontas (descartadas): https://github.com/stopwords-iso/stopwords-iso
- YouTube IFrame API (`loadVideoById` aceita `startSeconds` fracionário): https://developers.google.com/youtube/iframe_api_reference
- Parâmetros do player (`start` só inteiro): https://developers.google.com/youtube/player_parameters
- Corpus de teste (Brown, pacote do NLTK): https://raw.githubusercontent.com/nltk/nltk_data/gh-pages/packages/corpora/brown.zip
