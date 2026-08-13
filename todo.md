# Project Roadmap

## Current Milestone: Complete Frontend Authentication

Goal: prove the protected browser → API gateway → user-service flow end to end.

- [X] Move the API base URL out of `AuthService` and into Angular environment configuration.
- [ ] Add an HTTP interceptor that sends `Authorization: Bearer <access-token>` to protected API routes.
- [ ] Add an authentication guard and protect the `/dashboard` route.
- [ ] Redirect unauthenticated users to `/login`.
- [ ] Add `GET /users/me` to the frontend API service.
- [ ] Replace the dashboard placeholder with the authenticated user's profile.
- [ ] Add profile editing through `PUT /users/me` with validation and error states.
- [ ] Add temporary logout that clears the local access token and redirects to `/login`.
- [ ] Handle expired or rejected access tokens consistently.
- [ ] Add Vitest coverage for the auth service, interceptor, guard, and dashboard behavior.
- [ ] Verify registration, login, profile loading, profile updating, and logout through Docker Compose.

## Backend Hardening

- [ ] Add focused auth controller and JWT unit tests.
- [ ] Introduce Flyway migrations for `auth_db` and `user_db`.
- [ ] Replace Hibernate schema updates with schema validation after migrations are established.
- [ ] Add Testcontainers integration tests using PostgreSQL.

## Session Management

- [ ] Add Redis to Docker Compose and application configuration.
- [ ] Generate opaque refresh tokens and store them with a seven-day TTL.
- [ ] Return refresh tokens in secure, HTTP-only cookies.
- [ ] Implement `POST /auth/refresh` with token rotation.
- [ ] Implement `POST /auth/logout` and clear the refresh-token cookie.
- [ ] Add JWT revocation checks using each token's `jti`.
- [ ] Update the frontend to refresh expired access tokens automatically.

## Security and Operations

- [ ] Rate-limit registration and login attempts at the gateway.
- [ ] Move all non-development secrets to environment-managed configuration.
- [ ] Add production-oriented, multi-stage Docker images.
- [ ] Document production CORS, cookie, database, and Redis settings.

## Completion Checks

- [ ] `cd frontend && npm test && npm run build`
- [ ] Run `mvn test` in all three backend services.
- [ ] `docker compose -f infra/docker/docker-compose.yml config`
- [ ] Run the complete authentication flow against the Compose stack.
