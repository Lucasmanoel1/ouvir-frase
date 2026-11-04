// Relógio do vídeo para a legenda: segue o tempo do player do YouTube a cada quadro, sem tremer nem voltar para trás (puro, sem DOM).
// Como nos players de letra sincronizada: posição = âncora + (agora - âncora na parede) x velocidade,
// recalibrada quando o vídeo para, salta ou muda de velocidade.
const CORRECAO = 0.1; // parte do erro corrigida a cada leitura: puxa devagar para o tempo do player, sem salto visível

/**
 * `tolerancia` (s): diferença acima da qual o relógio salta para o player (seek, travada de rede) em vez de corrigir devagar.
 * `ler(tPlayer, agoraMs, taxa, tocando)`: `agoraMs` de performance.now(); `taxa` = velocidade; devolve o tempo a mostrar.
 */
export function criarRelogio(tolerancia = 0.25) {
  let ancora = null, ultimo = 0;
  const reancorar = (tPlayer, agoraMs, taxa) => {
    ancora = { midia: tPlayer, parede: agoraMs, taxa };
    ultimo = tPlayer;
    return tPlayer;
  };
  return {
    ler(tPlayer, agoraMs, taxa, tocando) {
      if (!tocando) { ancora = null; ultimo = tPlayer; return tPlayer; }
      if (!ancora || taxa !== ancora.taxa) return reancorar(tPlayer, agoraMs, taxa);
      const previsto = ancora.midia + ((agoraMs - ancora.parede) / 1000) * taxa;
      const erro = tPlayer - previsto;
      if (Math.abs(erro) > tolerancia) return reancorar(tPlayer, agoraMs, taxa);
      ancora.midia += erro * CORRECAO;
      ultimo = Math.max(ultimo, previsto + erro * CORRECAO);
      return ultimo;
    },
    zerar() { ancora = null; },
  };
}
