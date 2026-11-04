import test from 'node:test';
import assert from 'node:assert/strict';
import { palavras, indiceFalado } from '../../main/resources/static/js/palavras.js';

test('dá a posição de cada palavra no texto original, com pontuação no meio', () => {
  assert.deepEqual(palavras('Hello, world!'), [
    { texto: 'Hello', inicio: 0, fim: 5 },
    { texto: 'world', inicio: 7, fim: 12 },
  ]);
});

test('o apóstrofo reto ou curvo dentro da palavra não separa', () => {
  assert.deepEqual(palavras("don't"), [{ texto: "don't", inicio: 0, fim: 5 }]);
  assert.deepEqual(palavras('don’t'), [{ texto: 'don’t', inicio: 0, fim: 5 }]);
});

test('apóstrofo solto (aspas) não entra na palavra', () => {
  assert.deepEqual(palavras("'hello' 'world'"), [
    { texto: 'hello', inicio: 1, fim: 6 },
    { texto: 'world', inicio: 9, fim: 14 },
  ]);
});

test('chinês e japonês: um item por caractere, com posições', () => {
  assert.deepEqual(palavras('你好 abc'), [
    { texto: '你', inicio: 0, fim: 1 },
    { texto: '好', inicio: 1, fim: 2 },
    { texto: 'abc', inicio: 3, fim: 6 },
  ]);
  assert.deepEqual(palavras('日本語です').map((p) => p.texto), ['日', '本', '語', 'で', 'す']);
});

test('texto vazio ou ausente não tem palavras', () => {
  assert.deepEqual(palavras(''), []);
  assert.deepEqual(palavras(undefined), []);
});

test('indiceFalado: palavra longa ocupa mais tempo', () => {
  assert.equal(indiceFalado([1, 10], 0.05), 0);
  assert.equal(indiceFalado([1, 10], 0.2), 1);
});

test('indiceFalado: na fronteira entre palavras vale a seguinte', () => {
  assert.equal(indiceFalado([5, 5], 0.5), 1);
});

test('indiceFalado: antes do início é a primeira, depois do fim é a última', () => {
  assert.equal(indiceFalado([3, 4, 5], -0.5), 0);
  assert.equal(indiceFalado([3, 4, 5], 0), 0);
  assert.equal(indiceFalado([3, 4, 5], 1), 2);
  assert.equal(indiceFalado([3, 4, 5], 7), 2);
});

test('indiceFalado: sem palavras devolve -1', () => {
  assert.equal(indiceFalado([], 0.5), -1);
});
