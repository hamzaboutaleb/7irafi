# Project Roadmap

## Current Milestone: Complete Frontend Authentication

Goal: prove registration, login, and the protected browser → API gateway → user-service flow end to end.

- [x] Merge the frontend UI foundation while preserving the teammate's Git history.
- [x] Route development API requests through the gateway at `http://localhost:8080`.
- [x] Start the complete Compose stack and verify all backend health endpoints return `UP`.
- [x] Align the frontend authentication response type with the backend's direct `AuthResponse` shape.
- [x] Update login to read and save `response.accessToken` and display API errors safely.
- [x] Implement registration, save its token, navigate to `/home`, and verify registration and login in the browser.
- [x] Make landing-page authentication controls state-aware: route guests to Login/Sign up and show Logout when authenticated.
- [x] Match frontend registration and login validation to the backend's name, email, and password rules.
- [ ] Add an HTTP interceptor that sends `Authorization: Bearer <access-token>` to protected API routes.
- [x] Add authentication and guest guards, protect `/home`, and redirect unauthenticated users to `/login`.
- [x] Add basic `401` handling that clears the local token and redirects to `/login`.
- [ ] Add `GET /users/me` to the frontend API service.
- [ ] Replace the `/home` placeholder with the authenticated user's profile.
- [ ] Add profile editing through `PUT /users/me` with validation and error states.
- [ ] Handle expired or rejected access tokens consistently.
- [ ] Add Vitest coverage for the auth service, interceptor, guards, registration, login, and profile behavior.
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

- [x] Build the frontend with the development configuration inside Docker.
- [x] Verify gateway, auth-service, and user-service health endpoints through Compose.
- [ ] `cd frontend && npm test && npm run build`
- [ ] Run `mvn test` in all three backend services.
- [ ] `docker compose -f infra/docker/docker-compose.yml config`
- [ ] Run the complete authentication flow against the Compose stack.
