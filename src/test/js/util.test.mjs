import test from 'node:test';
import assert from 'node:assert/strict';
import { fatiaCircular } from '../../main/resources/static/js/util.js';

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
