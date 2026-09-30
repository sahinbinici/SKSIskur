#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WAR="$(find "$ROOT/target" -maxdepth 1 -name '*.war' | head -n 1)"
DIST="$ROOT/frontend/dist"

if [ -z "$WAR" ] || [ ! -f "$WAR" ]; then
  echo "WAR not found. Run: mvn -DskipTests package" >&2
  exit 1
fi
if [ ! -f "$DIST/index.html" ]; then
  echo "Frontend dist missing. Run: npm run build (in frontend/)" >&2
  exit 1
fi

STAGING="$(mktemp -d)"
trap 'rm -rf "$STAGING"' EXIT

mkdir -p "$STAGING/target" "$STAGING/frontend/dist"
cp "$ROOT/docker-compose.prod.yml" "$ROOT/Dockerfile.api.simple" "$ROOT/.dockerignore" "$STAGING/"
cp "$WAR" "$STAGING/target/"
cp "$ROOT/frontend/Dockerfile" "$ROOT/frontend/nginx.conf" "$STAGING/frontend/"
cp -a "$DIST/." "$STAGING/frontend/dist/"

tar -czf "$ROOT/sksiskur-deploy.tar.gz" -C "$STAGING" .
echo "Wrote $ROOT/sksiskur-deploy.tar.gz"
