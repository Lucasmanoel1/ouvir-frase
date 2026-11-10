-- A cada banco restaurado (o pg_restore --no-privileges nao leva permissoes).
-- Uso: psql -U postgres -d <banco> -f /sql/permissoes-app.sql
-- CONNECT no banco e USAGE no schema public ja vem de PUBLIC no Postgres 16.
GRANT SELECT ON ALL TABLES IN SCHEMA public TO ouvirfrase_app;
-- Sem USAGE: ninguem chama nextval. O SELECT basta para o ddl-auto=validate do Hibernate enxergar a ocorrencia_seq
-- (information_schema.sequences so lista sequences em que o usuario tem algum privilegio).
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO ouvirfrase_app;
