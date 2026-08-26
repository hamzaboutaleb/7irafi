# Repository Guidelines

## Project Structure & Module Organization

`frontend/` contains the Angular 21 application. Feature components live under `frontend/src/app/`, global styles are in `src/styles.css`, and static files belong in `public/` or `src/assets/`. `backend/` contains three independent Spring Boot services: `auth-service`, `user-service`, and `api-gateway`. Each follows the standard Maven layout (`src/main/java`, `src/main/resources`, and `src/test`). Docker development infrastructure and PostgreSQL initialization scripts live in `infra/docker/`. Design notes, specifications, and implementation guides are kept in `docs/`.

## Build, Test, and Development Commands

- `docker compose -f infra/docker/docker-compose.yml up --build` builds and runs PostgreSQL, all services, and the frontend.
- `cd frontend && npm ci` installs the locked frontend dependencies.
- `cd frontend && npm start` serves Angular at `http://localhost:4200` with live reload.
- `cd frontend && npm test` runs Vitest; `npm run build` creates a production build in `dist/`.
- `cd backend/auth-service && ./mvnw test` runs that service's tests. For `user-service` and `api-gateway`, use `mvn test` (Java 25 and Maven required).
- Run `mvn package` or `mvn spring-boot:run` from an individual backend service directory to package or start it.

## Coding Style & Naming Conventions

Frontend code follows `frontend/.editorconfig`: UTF-8, two-space indentation, final newlines, and single quotes in TypeScript. The package Prettier settings use a 100-character width. Keep Angular feature files together, using lowercase names such as `auth/login/login.ts`; use PascalCase for classes and camelCase for members. Java packages use `com.herafi.<service>`; use PascalCase types, camelCase methods/fields, four-space indentation, and descriptive suffixes such as `Controller`, `Service`, `Repository`, `Request`, and `Response`.

## Testing Guidelines

Backend tests use JUnit 5 and Spring Boot Test; persistence tests use H2 with the `test` profile. Mirror production packages under `src/test/java`, name classes `*Tests`, and name methods for behavior (for example, `rejectsDuplicateProfileId`). Place Angular tests beside their subjects as `*.spec.ts`. Add tests for changed validation, authorization, routing, and service behavior. No coverage threshold is currently enforced.

## Commit & Pull Request Guidelines

Recent history favors concise prefixes such as `feat:`, `docs:`, `refactor:`, and `chore:`. Prefer `<type>: <imperative summary>` and keep each commit focused. Pull requests should explain the change, list affected services, link relevant issues/specs, and report commands run. Include screenshots for visible UI changes and call out configuration, database, or API contract changes.

## Security & Configuration

Values in `infra/docker/docker-compose.yml` are development defaults only. Never commit production secrets. Configure database URLs, JWT secrets, internal API keys, service URLs, and allowed origins through environment variables.
