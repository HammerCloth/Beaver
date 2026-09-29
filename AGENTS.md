# Repository Guidelines

## Project Structure & Module Organization

The production application is split into `backend/` and `frontend-vue/`. The backend is a Spring Boot 3 service: Java code lives under `backend/src/main/java/com/zero/`, configuration and Flyway migrations under `backend/src/main/resources/`, and tests belong in `backend/src/test/java/`. The Vue 3 client keeps pages in `frontend-vue/src/views/`, reusable UI in `components/`, state in `stores/`, and HTTP clients in `api/`. Static assets are in `frontend-vue/public/` and `src/assets/`.

Deployment files are at the repository root (`docker-compose.yml`, `Caddyfile`), operational scripts are in `scripts/`, and behavior specifications are maintained in `openspec/specs/` with completed changes archived under `openspec/changes/archive/`.

## Build, Test, and Development Commands

- `cd backend && ./mvnw spring-boot:run` starts the API on port 8080.
- `cd backend && ./mvnw test` runs the Spring/JUnit test suite. Use JDK 21 (same as CI); on newer JDKs Mockito/Byte Buddy cannot mock classes.
- `cd frontend-vue && npm ci && npm run dev` installs locked dependencies and starts Vite on port 5173.
- `cd frontend-vue && npm run build` type-checks and creates `frontend-vue/dist/`.
- `docker compose up -d --build` builds and starts the production-style stack; `./scripts/deploy.sh` is the production path (builds, backs up the SQLite database to `backups/`, then restarts).
- `python3 scripts/seed_demo_data.py --reset --user <name> [--locale en]` rebuilds polished local demo data (backs up the DB first; refuses non-`backend/data/` databases without `--force`).

## Coding Style & Naming Conventions

Use two-space indentation in Vue, TypeScript, YAML, and JSON; use the existing four-space Java continuation style and package namespace `com.zero`. All user-visible frontend text goes through vue-i18n: add the same key to `frontend-vue/src/i18n/locales/zh-CN/<namespace>.ts` and `en-US/<namespace>.ts` and call `t()` imported from `@/i18n` (not `useI18n()`, which makes vue-tsc blow up on deep types). Switching language reloads the page, so evaluating `t()` at setup time is fine. Option labels from the settings store are user data and are only translated while they still equal the built-in defaults. Keep code comments in Chinese, matching the existing code.

Name Java types and Vue components in PascalCase (`DashboardService`, `SnapshotFormPage.vue`), functions and variables in camelCase, and database migrations `V<number>__<description>.sql`. Follow nearby code for quote and semicolon conventions. Keep controllers thin and place business rules in services.

## Testing Guidelines

Add JUnit tests under the matching backend package and name them `*Test.java`. The active Vue app currently has no automated test command, so `npm run build` is the minimum frontend check; manually verify affected routes and API states in both languages and at a 375px phone width (no horizontal scrolling; wide tables switch to card lists via `src/lib/mobileCard.ts`). Add regression coverage for bug fixes when a suitable harness exists.

## Commit & Pull Request Guidelines

Recent history follows Conventional Commits: `feat(vue): ...`, `fix: ...`, and `chore(openspec): ...`. Write imperative, focused subjects and include a scope when useful. Pull requests should explain the user-visible change, list verification commands, link the relevant issue or OpenSpec change, and include screenshots for UI changes. Call out migrations, environment-variable changes, and deployment impact explicitly; never commit `.env`, database files, or secrets.
