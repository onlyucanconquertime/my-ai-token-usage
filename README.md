# my-ai-token-usage — Token Farm

Shows your daily Claude Code token usage as a black/white/lime pixel-bar chart on your
GitHub profile, refreshed automatically several times a day. No server to keep running.

- Trailing 30-day window, days along the x-axis, tokens along the y-axis — **today is
  always the rightmost bar**. Each finished day is a chunky pixel bar shaded by how it
  ranks against the rest of the window (lime, increasing opacity toward the busiest
  days), with a little pixel-star on the window's peak days.
- **Today's bar is a dashed "ghost" outline**, not a ranked tier — the day isn't over, so
  its exact running token count is printed above it instead (e.g. `26,870,419 · today ·
  in progress`) rather than being compared to finished days.
- 1 block = a fixed 20M tokens (not derived from your data), so a normal day's usage
  doesn't just peg every bar at the chart's cap.
- Data comes from your local Claude Code logs (`~/.claude/projects/**/*.jsonl`).
- History lives in [`data/usage-history.json`](data/usage-history.json) — no database.

## How it stays automatic

1. **Local sync (every Claude Code session)** — a `SessionEnd` hook runs
   [`hooks/sync-usage.sh`](hooks/sync-usage.sh), which rescans your last 7 days of logs,
   updates `data/usage-history.json`, and pushes the change to this repo. It only needs
   your machine to be on at the moment you're actually using Claude Code.
2. **Publish 3x/day (8am, 2pm, 8pm Asia/Shanghai)** — [`.github/workflows/publish-token-farm.yml`](.github/workflows/publish-token-farm.yml)
   runs on GitHub's own cron, reads `data/usage-history.json` from this repo, renders the
   trailing-30-day-ending-today SVG, and commits it into your GitHub profile repo
   (`<username>/<username>`). No server, no ECS/VPS.

## One-time setup

**1. Register the sync hook** (globally, so it fires no matter which project you're
working in — add to `~/.claude/settings.json`):
```json
{
  "hooks": {
    "SessionEnd": [
      { "matcher": "", "hooks": [
        { "type": "command", "command": "/absolute/path/to/my-ai-token-usage/hooks/sync-usage.sh", "timeout": 30 }
      ]}
    ]
  }
}
```
Use the real absolute path to this repo on your machine. Build the jar once so the hook
has something to run: `mvn -f backend/pom.xml package -DskipTests`.

**2. Configure the publish workflow** — in this repo's GitHub settings:
- *Settings → Secrets and variables → Actions → Variables*: add `PROFILE_REPO` =
  `<your-username>/<your-username>` and `PROFILE_REPO_COMMIT_EMAIL` = an email for the bot
  commit author.
- *Settings → Secrets and variables → Actions → Secrets*: add `PROFILE_REPO_PAT` — a
  fine-grained GitHub PAT scoped to **contents: write** on just your profile repo.

**3. Embed the image** — add this once to your profile repo's own `README.md`:
```md
![token farm](https://raw.githubusercontent.com/<your-username>/<your-username>/main/token-farm.svg)
```

**4. Verify before trusting the cron** — run the workflow manually once via
*Actions → Publish token farm → Run workflow* and confirm a commit lands in your profile
repo, before relying on the 8am/2pm/8pm schedule.

## Estimated cost

`estimatedCost` in the history file is what your tokens *would* cost at Anthropic's API
list price (`backend/src/main/resources/application.yml`, under `app.pricing`) — Claude
Code subscription usage isn't billed per-token, so treat this as illustrative, not a real
charge. Edit the rates there if they drift from current pricing.

## Development

```bash
# Backend tests
mvn -f backend/pom.xml test

# Local dashboard (reads data/usage-history.json, never publishes anywhere)
# Run from the repo root — Maven forks spring-boot:run with backend/ as its working
# directory, so app.history-file-path needs an explicit absolute path here (relative
# paths with ".." don't bind cleanly to Spring's Path-typed properties).
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.arguments="--app.history-file-path=$(pwd)/data/usage-history.json"
cd frontend && npm install && npm run dev   # http://localhost:5173

# Manually run sync/render once, from the repo root
java -jar backend/target/token-farm.jar --spring.profiles.active=sync --spring.main.web-application-type=none
java -jar backend/target/token-farm.jar --spring.profiles.active=render --spring.main.web-application-type=none --app.svg-output-path=./out/token-farm.svg
```

Override defaults (timezone, rescan window, chart window, log location) via env vars —
see `backend/src/main/resources/application.yml` for the full list (`APP_TIMEZONE`,
`CLAUDE_LOGS_ROOT`, `RESCAN_WINDOW_DAYS`, `CHART_WINDOW_DAYS`, `HISTORY_FILE_PATH`).

## Layout

```
backend/    Spring Boot app — sync/render CLI profiles + local dashboard API
frontend/   Vue3 + Vite local dashboard (preview only, never publishes)
hooks/      SessionEnd hook script
data/       usage-history.json — the only "database"
.github/    the 8am/2pm/8pm publish workflow
```
