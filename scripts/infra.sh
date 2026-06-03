#!/usr/bin/env bash
# dev helper: manage the local infra (postgres, kafka, redis) for running the app from the ide
# usage: ./scripts/infra.sh [up|down|stop|logs|status]
set -euo pipefail

cd "$(dirname "$0")/.."

INFRA_SERVICES="postgres kafka redis"
cmd="${1:-up}"

case "$cmd" in
  up)
    docker compose up -d $INFRA_SERVICES
    echo "waiting for postgres..."
    for _ in $(seq 1 20); do
      if docker compose exec -T postgres pg_isready -U flightpulse >/dev/null 2>&1; then
        echo "infra ready: postgres:5432  kafka:9092  redis:6379"
        exit 0
      fi
      sleep 2
    done
    echo "postgres did not become ready in time; check 'docker compose logs postgres'" >&2
    exit 1
    ;;
  down)
    # stops and removes containers; keeps the postgres-data volume
    docker compose down
    ;;
  stop)
    docker compose stop $INFRA_SERVICES
    ;;
  logs)
    docker compose logs -f $INFRA_SERVICES
    ;;
  status)
    docker compose ps
    ;;
  *)
    echo "usage: $0 [up|down|stop|logs|status]" >&2
    exit 1
    ;;
esac
