-- Uma vez por servidor. O papel e do servidor (nao do banco), entao sobrevive a restauracoes.
-- Uso: docker compose exec -T db psql -U postgres -v senha="$IDIOMANDO_DB_SENHA" -f /sql/criar-papel-app.sql
CREATE ROLE idiomando_app LOGIN PASSWORD :'senha';

-- Gravados no papel, nao no postgresql.conf: valem so para o app, e o limite pega ate consulta que alguem esqueca de limitar.
ALTER ROLE idiomando_app SET statement_timeout = '3s';
ALTER ROLE idiomando_app SET default_transaction_read_only = on;
