#!/bin/bash
# =====================================================================
# 01-create-databases.sh
# Runs ONE time, when PostgreSQL starts for the first time (empty data volume).
# If the data volume already exists, this file is skipped.
#
# Production rule: ONE SERVICE = ONE DATABASE = ONE DATABASE USER.
#   auth-service       -> user auth_user       -> can open ONLY auth_db
#   experiment-service -> user experiment_user -> can open ONLY experiment_db
#   ... and so on. If one service is hacked, the other databases stay safe.
#
# The passwords are NOT written here. They come from environment variables that
# docker-compose gives to this container (from infra/docker/.env).
# =====================================================================

# A function = a named block of commands that we can run several times.
# It creates one login user and one database that this user owns.
#   $1 = database name   $2 = user name   $3 = password
create_service_db() {
  local db="$1" role="$2" pass="$3"
  if [ -z "$pass" ]; then
    echo "ERROR: no password for $role. Fill it in infra/docker/.env" >&2
    exit 1
  fi
  # psql = the PostgreSQL command line tool.
  # ON_ERROR_STOP=1 : stop at the first error.
  # :'pass'         : psql puts the password in safely (quotes are handled for us).
  psql -v ON_ERROR_STOP=1 -v pass="$pass" --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE ROLE ${role} LOGIN PASSWORD :'pass';
CREATE DATABASE ${db} OWNER ${role};
REVOKE ALL ON DATABASE ${db} FROM PUBLIC;
SQL
  echo "created database ${db} owned by user ${role}"
}

# REVOKE ... FROM PUBLIC above means: "nobody else may even connect to this database".

create_service_db auth_db       auth_user       "${AUTH_DB_PASSWORD:-}"
create_service_db experiment_db experiment_user "${EXPERIMENT_DB_PASSWORD:-}"
create_service_db simulation_db simulation_user "${SIMULATION_DB_PASSWORD:-}"
create_service_db kpi_db        kpi_user        "${KPI_DB_PASSWORD:-}"
create_service_db file_db       file_user       "${FILE_DB_PASSWORD:-}"
create_service_db ai_db         ai_user         "${AI_DB_PASSWORD:-}"