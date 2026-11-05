// Onde tocar um resultado da busca. O backend só devolve tempos do vídeo (a frase e as falas da legenda que a contêm);
// as folgas de tela ficam todas aqui.
const RECUO_PRIMEIRA_SEG = 3;   // primeira reprodução: recua até 3 s antes da frase
const RECUO_LOOP_SEG = 2.5;     // loop e tecla R: até 2,5 s antes
const LIMITE_FRASE_SEG = 5;     // no modo Frase da prática: nunca mais de 5 s antes nem depois da frase
const DURACAO_PADRAO_SEG = 8;   // se os tempos vierem incoerentes

/**
 * r = { inicioSeg, ultimaPalavraSeg, falaInicioSeg, falaFimSeg }; alvo = 'frase' | 'trecho' (o que a prática repete).
 * Início: recua, mas nunca começa depois do início da fala da frase. Se o recuo cai dentro dessa fala, começa no
 * começo dela; se cai na fala anterior, obedece o recuo. Fim do loop: o fim da fala.
 * No modo Frase, os dois lados ficam a no máximo 5 s da frase; no modo Trecho, valem as falas inteiras.
 * Devolve onde começa a primeira vez (inicio), onde o loop e a tecla R recomeçam (inicioLoop) e onde o loop volta (fim).
 */
export function janelaDoTrecho(r, alvo = 'frase') {
  const limite = alvo === 'frase' ? LIMITE_FRASE_SEG : Infinity;
  const antes = (recuo) => Math.max(0, r.inicioSeg - limite, Math.min(r.falaInicioSeg, r.inicioSeg - recuo));
  const inicioLoop = antes(RECUO_LOOP_SEG);
  let fim = Math.min(r.falaFimSeg, r.ultimaPalavraSeg + limite);
  if (!(fim > inicioLoop)) fim = inicioLoop + DURACAO_PADRAO_SEG;
  return { inicio: antes(RECUO_PRIMEIRA_SEG), inicioLoop, fim };
}
