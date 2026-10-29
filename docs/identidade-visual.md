# OuvirFrase: identidade visual e de voz

Este documento é a fonte da verdade para o visual do site e para qualquer vídeo, anúncio ou tutorial. Se uma decisão nova não couber aqui, o documento é atualizado antes.

## Essência

O OuvirFrase é uma professora amiga: calma, direta, sem enrolação e sem barulho. Nada de anúncio, cadastro ou tela lotada. O vídeo é o centro, e tudo o que você precisa para praticar está na mesma tela.

O ritmo da marca são três tempos: **Busque. Ouça. Repita.** Ele aparece na tela inicial, nos vídeos e em qualquer texto de apresentação.

Referência estética: papel claro, laranja quente, traço grosso de caneta marrom-escura, sombreado em hachura, como um caderno ilustrado. Não usamos personagens de terceiros: o logo é próprio.

## Cores

| Token | Valor | Uso |
|---|---|---|
| `--papel` | `#EEEBE3` | fundo da página (com grão sutil) |
| `--papel-2` | `#F8F6F0` | cartões, campos, botões |
| `--tinta` | `#2B2018` | texto e todos os traços |
| `--suave` | `#675C52` | texto secundário |
| `--laranja` | `#E17F53` | preenchimento de destaque (botão principal, progresso, realce da frase) |
| `--laranja-hover` | `#E98B63` | hover dos botões laranja |
| `--laranja-forte` | `#B8532A` | só para texto grande de marca ("Frase" no logo) |
| `--laranja-claro` | `#F6DCCD` | item ativo, selos, hover leve |
| `--ok` / `--ok-bg` | `#4D6B26` / `#DCE7C6` | palavra reconhecida |
| `--erro` / `--erro-bg` | `#9C3A22` / `#F4D2C6` | palavra não reconhecida |

Regras:
- Laranja é preenchimento, não cor de texto fino. Texto sobre laranja é sempre `--tinta`.
- Contraste mínimo de 4,5:1 para texto normal (3:1 só para texto grande). Todos os pares acima foram conferidos.
- Estado nunca depende só de cor: palavra errada também tem sublinhado ondulado.
- Não existe modo escuro. O site é claro por decisão.

## Traço e forma

- Borda de 2 px em `--tinta` em tudo que é interativo ou é cartão.
- Raio de 12 px nos botões, 14 px nos cartões, pílula (999 px) em idioma, chips e selos.
- Sombra dura deslocada, sem desfoque: `4px 4px 0 --tinta` nos cartões, `3px 3px 0` nos botões. No clique o botão "afunda" (desloca 3 px e perde a sombra).
- Hachura diagonal (`repeating-linear-gradient(135deg)`, 12% de tinta) em todo preenchimento laranja, ecoando o sombreado a lápis.
- Foco visível: contorno de 3 px em `--tinta`.

## Tipografia

Auto-hospedadas (sem CDN), pesos variáveis, subsets latin, latin-ext e cyrillic. Escritas fora desses subsets (chinês, japonês, coreano, árabe, hindi) usam a fonte do sistema.

- **Lora** (serifa): logo, títulos, a legenda (como citação) e as notas.
- **Inter**: toda a interface (botões, rótulos, textos de apoio).

## Ícones

Somente [Phosphor Icons](https://phosphoricons.com), peso **bold**, e **fill** para play, pause e stop. **Nunca emojis.** Os ícones usados ficam num sprite (`static/icons.svg`); para adicionar um, copie o símbolo do pacote `@phosphor-icons/core` (licença MIT) para o sprite com o id `i-<nome>`. Tamanho padrão `1.25em`, cor `currentColor`.

## Logo

Balão de fala laranja com contorno grosso e três barras (uma onda sonora) dentro. Nome em Lora bold: "Ouvir" em tinta, "Frase" em `--laranja-forte`. Sempre sobre `--papel` ou `--papel-2`. Arquivos: `static/favicon.svg` e o símbolo `logo` do sprite.

## Voz e textos (pt-BR)

- Frases curtas, verbos no imperativo: "Busque", "Ouça", "Repita".
- Fala com o usuário por "você", sem gíria, sem exclamação em excesso. Humor só se for discreto.
- Erro explica e oferece saída: "Não encontrei essa frase. Tente menos palavras ou outro idioma."
- Nunca "oops", nunca culpar o usuário.
- Promessa fixa: "Sem anúncios. Sem cadastro."

## Movimento

Curto e com função: barras de onda enquanto busca, entrada suave de cartões (≈ 300 ms), "pop" nas palavras da nota, pulso no botão enquanto escuta. Tudo é desligado quando o sistema pede menos movimento (`prefers-reduced-motion`).

## Roteiro de vídeo de 30 s (anúncio ou tutorial curto)

Gravar em 1920×1080 (ou 1366×768 para o texto ficar maior), idioma English, frase de exemplo "you know what". Estrutura: gancho, demonstração, chamada. Cada cena mostra uma coisa só.

| Tempo | Cena | Texto na tela / narração |
|---|---|---|
| 0–3 s | Tela inicial, cursor piscando no campo | "Você sabe o que a frase significa. Mas como ela soa?" |
| 3–8 s | Digita "you know what", Enter, as barras de onda, o estúdio abre | **Busque.** "Qualquer palavra ou frase." |
| 8–16 s | Vídeo toca, a frase fica destacada; liga o loop, escolhe 0,75× | **Ouça.** "Em vídeos de verdade. Em loop, mais devagar." |
| 16–25 s | Clica em Falar, fala; nota aparece, palavras verdes e uma vermelha | **Repita.** "Veja, palavra por palavra, o que o navegador entendeu." |
| 25–30 s | Logo centralizado sobre o papel | "Busque. Ouça. Repita." + "Sem anúncios. Sem cadastro." + endereço |

Para tutoriais mais longos (1 a 2 min), repita a estrutura por ferramenta: um problema, a ferramenta em uso, o resultado.
