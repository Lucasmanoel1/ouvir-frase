import test from 'node:test';
import assert from 'node:assert/strict';
import { janelaDoTrecho } from '../../main/resources/static/js/janela.js';

const perto = (a, b) => assert.ok(Math.abs(a - b) < 1e-9, `${a} != ${b}`);

test('frase que abre a fala: obedece o recuo e começa na fala anterior (3 s na 1ª vez, 2,5 s no loop)', () => {
  const j = janelaDoTrecho({ inicioSeg: 10, ultimaPalavraSeg: 10.8, falaInicioSeg: 10, falaFimSeg: 14 });
  perto(j.inicio, 7);
  perto(j.inicioLoop, 7.5);
  perto(j.fim, 14);
});

test('recuo cai dentro da fala da frase: começa no início dela e o loop termina no fim dela', () => {
  const j = janelaDoTrecho({ inicioSeg: 24, ultimaPalavraSeg: 24.5, falaInicioSeg: 20, falaFimSeg: 27 });
  perto(j.inicio, 20);
  perto(j.inicioLoop, 20);
  perto(j.fim, 27);
});

test('fala longa no modo Frase: no máximo 5 s antes e 5 s depois da frase', () => {
  const j = janelaDoTrecho({ inicioSeg: 29, ultimaPalavraSeg: 30, falaInicioSeg: 20, falaFimSeg: 45 }, 'frase');
  perto(j.inicio, 24);
  perto(j.inicioLoop, 24);
  perto(j.fim, 35);
});

test('a mesma fala longa no modo Trecho: do início ao fim da fala', () => {
  const j = janelaDoTrecho({ inicioSeg: 29, ultimaPalavraSeg: 30, falaInicioSeg: 20, falaFimSeg: 45 }, 'trecho');
  perto(j.inicio, 20);
  perto(j.inicioLoop, 20);
  perto(j.fim, 45);
});

test('começo do vídeo: nunca negativo', () => {
  const j = janelaDoTrecho({ inicioSeg: 0.3, ultimaPalavraSeg: 1, falaInicioSeg: 0.1, falaFimSeg: 3 });
  assert.equal(j.inicio, 0);
  assert.equal(j.inicioLoop, 0);
});

test('tempos incoerentes (fim da fala ausente): o loop dura 8 s', () => {
  const j = janelaDoTrecho({ inicioSeg: 20, ultimaPalavraSeg: 21, falaInicioSeg: 15 });
  perto(j.fim, j.inicioLoop + 8);
});
