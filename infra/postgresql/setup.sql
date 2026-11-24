\set ON_ERROR_STOP on
\getenv app_user APP_DB_USER
\getenv app_password APP_DB_PASSWORD
\getenv app_db APP_DB_NAME
\getenv test_db APP_TEST_DB_NAME

SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname = :'app_user' AND (rolsuper OR rolcreatedb OR rolcreaterole OR NOT rolcanlogin)) AS unsafe_role \gset
\if :unsafe_role
  \echo 'The application role must be a dedicated, unprivileged login.'
  \quit 1
\endif

SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', :'app_user', :'app_password')
WHERE NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname = :'app_user') \gexec

SELECT format('CREATE DATABASE %I OWNER %I ENCODING %L TEMPLATE template0', :'app_db', :'app_user', 'UTF8')
WHERE NOT EXISTS(SELECT 1 FROM pg_database WHERE datname = :'app_db') \gexec
SELECT format('CREATE DATABASE %I OWNER %I ENCODING %L TEMPLATE template0', :'test_db', :'app_user', 'UTF8')
WHERE NOT EXISTS(SELECT 1 FROM pg_database WHERE datname = :'test_db') \gexec

SELECT NOT EXISTS (
  SELECT 1 FROM pg_database d JOIN pg_roles r ON r.oid = d.datdba
  WHERE d.datname IN (:'app_db', :'test_db') AND (r.rolname <> :'app_user' OR pg_encoding_to_char(d.encoding) <> 'UTF8')
) AS valid_databases \gset
\if :valid_databases
  \echo 'Application databases are ready.'
\else
  \echo 'Existing databases must be UTF-8 and owned by the application role. No ownership was changed.'
  \quit 1
\endif
