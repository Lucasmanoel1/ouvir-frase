#!/usr/bin/env bash
# Desfaz a ultima atualizacao do indice: troca ouvirfrase <-> ouvirfrase_antigo.
# Rodar de novo desfaz a volta. Rodar na VPS, em /opt/ouvirfrase.
set -euo pipefail
cd "$(dirname "$0")/.."

# </dev/null: o exec -T le o stdin.
dc() { docker compose "$@" </dev/null; }
psql_pg() { dc exec -T db psql -U postgres -v ON_ERROR_STOP=1 "$@"; }

existe="$(psql_pg -d postgres -tA -c "SELECT 1 FROM pg_database WHERE datname = 'ouvirfrase_antigo'")"
if [ "$existe" != "1" ]; then
  echo "Nao existe ouvirfrase_antigo: nao ha para onde voltar." >&2
  exit 1
fi

dc stop app
trap 'echo "A troca falhou. Subindo o app de novo com o banco que estava no ar." >&2; dc start app || true' ERR
psql_pg -d postgres \
  -c "ALTER DATABASE ouvirfrase RENAME TO ouvirfrase_tmp" \
  -c "ALTER DATABASE ouvirfrase_antigo RENAME TO ouvirfrase" \
  -c "ALTER DATABASE ouvirfrase_tmp RENAME TO ouvirfrase_antigo"
trap - ERR
dc start app

for _ in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    echo "App no ar com o indice anterior."
    exit 0
  fi
  sleep 2
done
echo "O app nao respondeu em 60 s. Veja: docker compose logs app" >&2
exit 1
