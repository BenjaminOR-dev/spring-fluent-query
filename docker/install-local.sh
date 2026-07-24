#!/bin/sh
set -e

ROOT_DIR="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"

echo "Instalando spring-fluent-query en el repositorio Maven local (.m2 del proyecto)..."
echo "  origen: $ROOT_DIR"

cd "$ROOT_DIR"
docker compose run --rm maven mvn clean install -DskipTests -Dgpg.skip=true

echo ""
echo "Listo. Puedes usar io.github.benjaminor-dev:spring-fluent-query-spring-boot-starter:${PROJECT_VERSION:-0.2.0}"
echo "desde tu app local apuntando al .m2 del volumen Docker o instalando también en ~/.m2."
