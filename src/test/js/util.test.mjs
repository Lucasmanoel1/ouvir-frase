import test from 'node:test';
import assert from 'node:assert/strict';
import { fatiaCircular, faltaEsperar, proximoIndice } from '../../main/resources/static/js/util.js';

const lista = ['a', 'b', 'c', 'd', 'e'];

test('fatiaCircular pega n itens a partir do início', () => {
  assert.deepEqual(fatiaCircular(lista, 0, 3), ['a', 'b', 'c']);
});

test('fatiaCircular dá a volta no fim da lista', () => {
  assert.deepEqual(fatiaCircular(lista, 3, 3), ['d', 'e', 'a']);
  assert.deepEqual(fatiaCircular(lista, 7, 3), ['c', 'd', 'e']);
});

test('fatiaCircular com lista menor que n devolve a lista sem repetir', () => {
  assert.deepEqual(fatiaCircular(['a', 'b'], 1, 3), ['b', 'a']);
});

test('fatiaCircular com lista vazia devolve vazio', () => {
  assert.deepEqual(fatiaCircular([], 0, 3), []);
});

test('faltaEsperar completa o tempo mínimo desde o início', () => {
  assert.equal(faltaEsperar(1000, 1200, 1500), 1300);
});

test('faltaEsperar é zero quando o mínimo já passou', () => {
  assert.equal(faltaEsperar(1000, 2500, 1500), 0);
  assert.equal(faltaEsperar(1000, 9000, 1500), 0);
});

// proximoIndice: `pula(i)` diz se o item i está indisponível.
const pulando = (...fora) => (i) => fora.includes(i);

test('proximoIndice anda para a frente quando nada é pulado', () => {
  assert.equal(proximoIndice(5, 1, 1, pulando()), 2);
});

test('proximoIndice pula um indisponível no meio', () => {
  assert.equal(proximoIndice(5, 1, 1, pulando(2)), 3);
});

test('proximoIndice pula vários seguidos', () => {
  assert.equal(proximoIndice(5, 0, 1, pulando(1, 2, 3)), 4);
});

test('proximoIndice devolve -1 quando todos à frente estão indisponíveis', () => {
  assert.equal(proximoIndice(4, 1, 1, pulando(2, 3)), -1);
  assert.equal(proximoIndice(4, 3, 1, pulando()), -1);
});

test('proximoIndice anda para trás pulando', () => {
  assert.equal(proximoIndice(5, 4, -1, pulando(3, 2)), 1);
});

test('proximoIndice devolve -1 no começo da lista com delta -1', () => {
  assert.equal(proximoIndice(5, 0, -1, pulando()), -1);
});
