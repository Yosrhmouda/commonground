#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
(cd frontend && npm ci && npm run build)
mkdir -p backend/src/main/resources/static
cp -R frontend/dist/commonground/browser/. backend/src/main/resources/static/
(cd backend && ./mvnw clean package)
