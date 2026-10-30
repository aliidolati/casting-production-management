#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
java -version
# Run in the Cloud setup phase while dependency downloads are allowed.
# No application startup, source database access or runtime credentials are needed.
bash mvnw -B -ntp -Pmysql-it -DskipTests dependency:go-offline test-compile
