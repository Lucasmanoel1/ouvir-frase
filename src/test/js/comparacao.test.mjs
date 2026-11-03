import test from 'node:test';
import assert from 'node:assert/strict';
import { tokens, comparar, deveMostrarOuvido, alternativasDe } from '../../main/resources/static/js/comparacao.js';

test('tokens tira marcações de som e normaliza o apóstrofo curvo', () => {
  assert.deepEqual(tokens('Hello [Music] don’t (laughs) STOP'), ['hello', "don't", 'stop']);
});

test('comparar ignora acento e apóstrofo', () => {
  assert.equal(comparar('está bem', 'esta bem').pontuacao, 1);
  assert.equal(comparar("don't go", 'dont go').pontuacao, 1);
});

test('comparar conta as palavras casadas e marca as que faltaram', () => {
  const c = comparar('one two three', 'one three');
  assert.equal(c.casadas, 2);
  assert.ok(Math.abs(c.pontuacao - 2 / 3) < 1e-9);
  assert.deepEqual(c.palavras.map((p) => p.ok), [true, false, true]);
});

test('comparar respeita a ordem das palavras', () => {
  assert.equal(comparar('a b', 'b a').casadas, 1);
});

test('"Entendi" não aparece quando acertou tudo e não sobrou nada', () => {
  assert.equal(deveMostrarOuvido(comparar('thank you very much', 'thank you very much')), false);
});

test('"Entendi" aparece com palavra a mais, mesmo com 100%', () => {
  const c = comparar('thank you', 'well thank you');
  assert.equal(c.pontuacao, 1);
  assert.equal(deveMostrarOuvido(c), true);
});

test('"Entendi" aparece quando a nota é menor que 100%', () => {
  assert.equal(deveMostrarOuvido(comparar('one two three four five', 'one two three')), true);
});

test('alternativasDe: sem resultado final devolve vazio (parciais são ignorados)', () => {
  assert.deepEqual(alternativasDe([]), []);
  assert.deepEqual(alternativasDe([{ final: false, alternativas: ['thank'] }]), []);
});

test('alternativasDe: um final devolve as alternativas dele', () => {
  const r = [{ final: true, alternativas: ['a', 'b', 'c'] }, { final: false, alternativas: ['x'] }];
  assert.deepEqual(alternativasDe(r), ['a', 'b', 'c']);
});

test('alternativasDe: dois finais viram uma frase só, com a melhor alternativa de cada', () => {
  const r = [
    { final: true, alternativas: ['thank you', 'tank you'] },
    { final: true, alternativas: ['very much', 'berry much'] },
  ];
  assert.deepEqual(alternativasDe(r), ['thank you very much']);
});
