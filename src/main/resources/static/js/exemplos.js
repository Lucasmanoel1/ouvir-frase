// Frases de exemplo por idioma (só dados; a lógica está em historico.js).
// Toda frase precisa dar resultado exato no idioma: escolher das **Sugestões de exemplos** do painel de ingestão (só saem frases do índice).
// Só entram idiomas já indexados de fato.
export const EXEMPLOS = {
  // as 3 primeiras eram os exemplos antigos; o resto saiu da consulta (cada frase em >= 9 vídeos)
  en: [
    'thank you very much', 'you know what', 'a lot of',
    'thank you', 'for example', 'right now', 'years ago', 'of course', 'a little bit', "i don't know",
    'i want to', 'talk about', 'think about', "that's why", 'be able to', 'instead of', 'figure out',
    'every day', 'make sure', 'social media', 'no matter', 'turns out', 'last year', 'all the time',
    'each other', 'around the world', 'in front of',
  ],
  // da consulta (cada frase em >= 10 vídeos), as que soam naturais sozinhas
  es: [
    'muchas gracias', 'por qué', 'por ejemplo', 'a veces', 'cada vez', 'sin embargo', 'no hay', 'por eso',
    'vamos a', 'tiene que', 'creo que', 'hay que', 'un poco', 'así que', 'de hecho', 'es decir', 'en realidad',
    'de repente', 'al final', 'sobre todo', 'la verdad', 'me gusta', 'no sé', 'un montón de', 'en vez de',
    'la mayoría de',
  ],
  // da consulta (poucos vídeos em russo ainda: cada frase está em 3 a 6)
  ru: [
    'на самом деле', 'потому что', 'может быть', 'мне кажется', 'в том числе', 'всё равно', 'до сих пор',
    'к сожалению', 'даже если', 'как будто', 'каждый раз', 'несмотря на', 'скорее всего', 'не только',
    'очень много', 'никогда не', 'вы можете', 'у меня есть', 'что происходит', 'а что если', 'ничего не',
    'это просто', 'думаю что', 'не просто', 'вообще не',
  ],
};
