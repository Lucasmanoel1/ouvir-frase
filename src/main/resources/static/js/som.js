// Som dos pingos dos "i" (js/titulo.js): duas batidas de madeira, vindas de um único arquivo WAV.
// O arquivo tem as batidas a 120 ms uma da outra e os saltos têm 150 ms; por isso cada batida é recortada
// e tocada no instante do seu pingo.
// Os navegadores só liberam áudio depois de um clique ou toque na página. Sem isso, a animação roda em silêncio.
const ARQUIVO = 'audio/idiomando-madeira-curta.wav';
const VOLUME = 0.7;

// Em segundos dentro do arquivo: onde o recorte começa e termina, e o instante em que a batida ataca.
const BATIDAS = [
  { de: 0.030, ate: 0.148, ataque: 0.0353 },
  { de: 0.146, ate: 0.560, ataque: 0.1551 },
];

/** Devolve { tocar, pausar, retomar, parar }, ou null se não há áudio liberado ou o arquivo não carregou. */
export async function prepararSom() {
  const Contexto = window.AudioContext || window.webkitAudioContext;
  if (!Contexto) return null;
  let ctx;
  try {
    ctx = new Contexto();
    const resposta = await fetch(ARQUIVO);
    const buffer = await ctx.decodeAudioData(await resposta.arrayBuffer());
    if (ctx.state !== 'running') {
      // sem toque do usuário o navegador mantém o contexto parado: não esperamos por ele
      await Promise.race([ctx.resume().catch(() => {}), new Promise((r) => setTimeout(r, 200))]);
    }
    if (ctx.state !== 'running') { ctx.close(); return null; }

    return {
      // `ms`: quanto falta, a partir de agora, para o pingo começar o salto
      tocar(indice, ms) {
        if (ms < -20 || ctx.state === 'closed') return; // já passou, ou o som já foi encerrado
        const b = BATIDAS[indice];
        const latencia = ctx.outputLatency || ctx.baseLatency || 0;
        const noAtaque = ctx.currentTime + Math.max(0, ms / 1000 - latencia);
        const inicio = noAtaque - (b.ataque - b.de);
        const duracao = b.ate - b.de;
        const fonte = ctx.createBufferSource();
        fonte.buffer = buffer;
        const ganho = ctx.createGain();
        // entrada e saída de poucos ms, para o recorte não estalar
        ganho.gain.setValueAtTime(0, inicio);
        ganho.gain.linearRampToValueAtTime(VOLUME, inicio + 0.002);
        ganho.gain.setValueAtTime(VOLUME, inicio + duracao - 0.008);
        ganho.gain.linearRampToValueAtTime(0, inicio + duracao);
        fonte.connect(ganho).connect(ctx.destination);
        fonte.start(inicio, b.de, duracao);
      },
      // o relógio do áudio para junto com o da animação quando a aba fica oculta
      pausar() { return ctx.suspend(); },
      retomar() { return ctx.resume(); },
      // a busca começou: o que ainda estava agendado (ou vier depois) não toca mais
      parar() { return ctx.state === 'closed' ? undefined : ctx.close().catch(() => {}); },
    };
  } catch (e) {
    if (ctx) ctx.close().catch(() => {});
    return null;
  }
}
