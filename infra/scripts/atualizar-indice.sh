#!/usr/bin/env bash
# Troca o indice de producao por um dump novo, guardando o anterior em idiomando_antigo
# (para voltar: ./scripts/voltar-indice.sh). Rodar na VPS, em /opt/idiomando.
# Uso: ./scripts/atualizar-indice.sh idiomando-AAAA-MM-DD.dump
set -euo pipefail

DUMP="${1:-}"
if [ -z "$DUMP" ] || [ ! -f "$DUMP" ]; then
  echo "Uso: $0 arquivo.dump (arquivo nao encontrado)" >&2
  exit 1
fi
DUMP="$(realpath "$DUMP")"
cd "$(dirname "$0")/.."

# </dev/null: o exec -T le o stdin e engoliria a resposta do "Trocar?".
dc() { docker compose "$@" </dev/null; }
psql_pg() { dc exec -T db psql -U postgres -v ON_ERROR_STOP=1 "$@"; }
contar() {
  psql_pg -d "$1" -tA -c "SELECT (SELECT count(*) FROM video) || ' videos, ' || (SELECT count(*) FROM ocorrencia) || ' ocorrencias'"
}

echo "1/6 Copiando o dump para o conteiner..."
dc cp "$DUMP" db:/tmp/indice.dump

echo "2/6 Criando idiomando_novo..."
psql_pg -d postgres -c "DROP DATABASE IF EXISTS idiomando_novo" -c "CREATE DATABASE idiomando_novo TEMPLATE template0"

echo "3/6 Restaurando (pode levar alguns minutos)..."
dc exec -T db pg_restore -U postgres -d idiomando_novo --no-owner --no-privileges --exit-on-error /tmp/indice.dump

echo "4/6 Permissoes do app e ANALYZE..."
psql_pg -d idiomando_novo -f /sql/permissoes-app.sql
psql_pg -d idiomando_novo -c "ANALYZE"

echo "5/6 Conferindo:"
echo "  atual: $(contar idiomando)"
echo "  novo:  $(contar idiomando_novo)"
read -r -p "Trocar? (s/N) " resposta
if [ "$resposta" != "s" ]; then
  echo "Cancelado. O banco atual continua no ar; idiomando_novo ficou para a proxima rodada."
  dc exec -T db rm -f /tmp/indice.dump
  exit 0
fi

echo "6/6 Trocando (o site fica fora do ar por alguns segundos)..."
dc stop app
trap 'echo "A troca falhou. Subindo o app de novo com o banco que estava no ar." >&2; dc start app || true' ERR
psql_pg -d postgres \
  -c "DROP DATABASE IF EXISTS idiomando_antigo" \
  -c "ALTER DATABASE idiomando RENAME TO idiomando_antigo" \
  -c "ALTER DATABASE idiomando_novo RENAME TO idiomando"
trap - ERR
dc start app

for _ in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    echo "App no ar. Idiomas indexados:"
    curl -fsS http://127.0.0.1:8080/api/idiomas
    echo
    dc exec -T db rm -f /tmp/indice.dump
    exit 0
  fi
  sleep 2
done
echo "O app nao respondeu em 60 s. Veja: docker compose logs app. Para voltar: ./scripts/voltar-indice.sh" >&2
exit 1
