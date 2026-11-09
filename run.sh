#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
echo 'Starting CommonGround. Open http://localhost:8080 after startup.'
exec java -jar release/commonground-1.0.0.jar
