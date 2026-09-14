#!/usr/bin/env bash
# Invoked by Claude Code's SessionEnd hook (registered in ~/.claude/settings.json, see
# README.md) after every session, on every project. Rescans recent Claude Code logs,
# updates data/usage-history.json, and pushes the change if anything's new.
set -euo pipefail
cd "$(dirname "$0")/.."

java -jar backend/target/token-farm.jar \
  --spring.profiles.active=sync --spring.main.web-application-type=none

git add data/usage-history.json
if ! git diff --cached --quiet; then
  git commit -m "chore: sync usage $(date -u +%Y-%m-%dT%H:%M:%SZ)" --quiet
  git push --quiet
fi
