import test from 'node:test';
import assert from 'node:assert/strict';
import { videoIndisponivel } from '../../main/resources/static/js/player.js';

// Códigos do onError da IFrame API: só 100, 101 e 150 tornam o vídeo indisponível.
test('videoIndisponivel vale só para vídeo removido, privado ou com incorporação bloqueada', () => {
  for (const codigo of [100, 101, 150]) assert.equal(videoIndisponivel(codigo), true, 'código ' + codigo);
  for (const codigo of [2, 5, 153]) assert.equal(videoIndisponivel(codigo), false, 'código ' + codigo);
});
