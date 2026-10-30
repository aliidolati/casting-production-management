#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
case "${1:-fast}" in
  fast) bash mvnw -B -ntp test ;;
  mysql)
    command -v docker >/dev/null || { echo 'MySQL tests require Docker; use the GitHub Actions mysql job.' >&2; exit 1; }
    docker info >/dev/null
    bash mvnw -B -ntp -Pmysql-it -DskipUnitTests=true verify
    ;;
  *) echo 'Usage: bash scripts/test-backend.sh [fast|mysql]' >&2; exit 2 ;;
esac
