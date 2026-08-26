# Auth, User, and Gateway Development Progress

Last updated: June 25, 2026

This file records the implementation sequence and integration contracts so work can
continue without relying on conversation history.

## Architecture Decisions

- Each backend application runs in its own container.
- Local development uses one PostgreSQL container with separate databases:
  - `auth_db` belongs to auth-service.
  - `user_db` belongs to user-service.
- Auth-service owns:
  - Email and password credentials.
  - Roles.
  - The canonical user UUID.
  - Access-token generation.
- User-service owns:
  - First name.
  - Last name.
  - Profile timestamps.
- Auth-service creates the UUID before saving credentials and sends the same UUID to
  user-service. This links both databases without sharing tables.
- The API gateway validates JWT access tokens, extracts claims, removes
  untrusted identity headers, and injects trusted identity headers for downstream
  services.

## Ports

| Component | Local port | Container port |
| --- | ---: | ---: |
| Frontend | 4200 | 4200 |
| API gateway | 8080 | 8080 |
| Auth-service | 8081 | 8081 |
| User-service | 8082 | 8082 |
| PostgreSQL | 5432 | 5432 |

## PostgreSQL

Docker Compose service: `postgres`

Development credentials:

```text
username: herafi
password: herafi
```

Database initialization file:

```text
infra/docker/postgres/init/01-create-databases.sql
```

It explicitly creates:

```text
auth_db
user_db
```

Important: PostgreSQL initialization scripts only run when the data volume is first
created. The existing development volume was created before `user_db` was added, so
`user_db` was manually created with:

```bash
docker exec postgres-dev createdb -U herafi user_db
```

## Auth-Service Progress

Location:

```text
backend/auth-service
```

Container:

```text
auth-service-dev
```

### Implemented model

`UserCredential` contains:

- UUID `id`
- Unique normalized `email`
- BCrypt `passwordHash`, stored in the `password` column
- `Role` enum: `USER`, `ADMIN`
- `createdAt`
- `updatedAt`

JPA lifecycle callbacks set creation and update timestamps.

### Implemented repository

`UserCredentialRepository`:

```java
Optional<UserCredential> findByEmail(String email);
boolean existsByEmail(String email);
```

### Implemented registration

`POST /auth/register`

Request:

```json
{
  "email": "user@example.com",
  "password": "secret123",
  "firstName": "Youssef",
  "lastName": "Rachidi"
}
```

Registration flow:

1. Validate request.
2. Normalize email using trim and lowercase.
3. Reject duplicate email.
4. Generate canonical user UUID.
5. Hash password using BCrypt.
6. Save credentials in `auth_db`.
7. Call user-service `POST /internal/users`.
8. If profile creation fails, throw an exception so the credential transaction rolls
   back.
9. Generate an access token.
10. Return HTTP `201`.

### Implemented login

`POST /auth/login`

Request:

```json
{
  "email": "user@example.com",
  "password": "secret123"
}
```

Login:

- Normalizes email.
- Uses BCrypt password comparison.
- Returns the same error for unknown email and incorrect password.
- Uses Spring Security's `DaoAuthenticationProvider`, which includes timing-attack
  mitigation for unknown users.
- Generates a signed JWT access token.

### Access token

- Algorithm: HS256.
- Default lifetime: 15 minutes.
- Issuer: `7irafi-auth-service`.
- Claims:
  - `jti`
  - `sub`: canonical user UUID
  - `email`
  - `role`
  - `iat`
  - `exp`

Auth response:

```json
{
  "accessToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "...",
    "email": "user@example.com",
    "role": "USER"
  }
}
```

### Auth-service security

- Stateless Spring Security filter chain.
- CSRF disabled for the JSON API.
- Form login and HTTP Basic disabled.
- Public:
  - `/auth/register`
  - `/auth/login`
  - `/actuator/health`
- Other auth-service routes are denied until explicitly implemented.

### Auth-service to user-service

URL property:

```properties
services.user-service.url
```

Container value:

```text
http://user-service:8082
```

Internal request header:

```text
X-Internal-Api-Key
```

Development value:

```text
7irafi-development-internal-key
```

### Auth-service errors

Implemented:

- `EMAIL_ALREADY_IN_USE` → 409
- `INVALID_CREDENTIALS` → 401
- `USER_SERVICE_UNAVAILABLE` → 503
- `VALIDATION_ERROR` → 400

### Auth-service verification

- `mvn test` passes.
- Test profile uses an H2 in-memory database.
- Context-load test passes.
- Container starts successfully and connects to PostgreSQL 17.
- Registration and login were verified through the API gateway.
- Registration successfully created matching UUID records in `auth_db` and `user_db`.

## User-Service Progress

Location:

```text
backend/user-service
```

Container:

```text
user-service-dev
```

### Implemented model

`UserProfile` contains:

- UUID `id`, supplied by auth-service
- Unique normalized `email`
- `firstName`
- `lastName`
- `createdAt`
- `updatedAt`

Table:

```text
user_profiles
```

### Implemented endpoints

#### Internal profile creation

```http
POST /internal/users
X-Internal-Api-Key: <internal key>
```

Request:

```json
{
  "id": "canonical-auth-user-uuid",
  "email": "user@example.com",
  "firstName": "Youssef",
  "lastName": "Rachidi"
}
```

This endpoint is intended for auth-service, not browsers or the gateway.

#### Get authenticated profile

```http
GET /users/me
X-Gateway-Key: <gateway shared secret>
X-User-Id: <JWT subject UUID>
```

#### Update authenticated profile

```http
PUT /users/me
X-Gateway-Key: <gateway shared secret>
X-User-Id: <JWT subject UUID>
Content-Type: application/json
```

Request:

```json
{
  "firstName": "Updated",
  "lastName": "Name"
}
```

### User-service trust boundary

`TrustedCallerFilter` protects service entry points:

- `/users/**` requires the gateway shared secret in `X-Gateway-Key`.
- `/internal/**` requires the internal service key in `X-Internal-Api-Key`.
- `/actuator/health` remains available for health checks.

Development gateway key:

```text
7irafi-development-gateway-key
```

This prevents a direct development caller from authenticating merely by forging
`X-User-Id`.

### User-service errors

Implemented:

- `USER_PROFILE_NOT_FOUND` → 404
- `USER_PROFILE_ALREADY_EXISTS` → 409
- `PROFILE_EMAIL_ALREADY_IN_USE` → 409
- `MISSING_USER_IDENTITY` → 401
- `UNTRUSTED_GATEWAY` → 401
- `UNAUTHORIZED_INTERNAL_REQUEST` → 401
- `VALIDATION_ERROR` → 400

### User-service verification

- `mvn test` passes.
- Five tests pass:
  - Application context.
  - Profile normalization and creation.
  - Duplicate profile rejection.
  - Profile update.
  - Missing profile rejection.
- Tests use an H2 in-memory database.

## Docker

Dockerfiles exist for:

- `backend/auth-service/Dockerfile`
- `backend/user-service/Dockerfile`
- `backend/api-gateway/Dockerfile`

Compose defines separate containers for:

- PostgreSQL
- Auth-service
- User-service
- API gateway
- Frontend

The local-development Dockerfiles use a single Maven/JDK stage so they can reuse one
downloaded base image. This makes development images larger but avoids downloading a
second runtime image. Slim multi-stage production images can be restored later.

## API Gateway

Location:

```text
backend/api-gateway
```

Container:

```text
api-gateway-dev
```

The implemented gateway:

1. Listen on port `8080`.
2. Route public auth calls:
   - `/auth/**` → `http://auth-service:8081`
3. Validate HS256 JWT access tokens for protected routes using the same JWT secret.
4. Validate issuer `7irafi-auth-service`.
5. Route:
   - `/users/**` → `http://user-service:8082`
6. Remove any incoming client values for:
   - `X-User-Id`
   - `X-User-Email`
   - `X-User-Role`
   - `X-Gateway-Key`
   - `X-Internal-Api-Key`
7. Add trusted downstream headers from JWT claims:
   - `X-User-Id` from `sub`
   - `X-User-Email` from `email`
   - `X-User-Role` from `role`
   - `X-Gateway-Key` using the configured gateway shared secret
8. Never expose or route `/internal/**`.

The gateway is a Spring Security OAuth2 resource server. It validates the token
signature and issuer before the request reaches a protected route.

Shared gateway configuration:

```text
JWT secret: 7irafi-development-jwt-secret-change-me-2026
JWT issuer: 7irafi-auth-service
Gateway key: 7irafi-development-gateway-key
```

These development secrets must be replaced by environment-managed secrets before
production.

### Gateway verification

- `mvn test` passes.
- The gateway container starts successfully.
- `GET /actuator/health` returns `UP`.
- `POST /auth/register` through port 8080 returns HTTP 201.
- `POST /auth/login` through port 8080 returns HTTP 200 and a signed access token.
- Authenticated `GET /users/me` returns the profile created during registration.
- Authenticated `PUT /users/me` persists profile changes.
- `GET /users/me` without a token returns HTTP 401.
- Direct `GET /users/me` on port 8082 without the gateway key returns
  `UNTRUSTED_GATEWAY`.
- Client-supplied identity headers are replaced with claims from the verified JWT.
- Invalid login credentials return `INVALID_CREDENTIALS`.

### Final test results

Verified on June 25, 2026:

```text
auth-service: 1 test passed
user-service: 5 tests passed
api-gateway: 4 tests passed
Docker Compose configuration: valid
End-to-end gateway flow: passed
```

## Deferred Auth Tasks

These are intentionally not required for the first gateway connection:

- Opaque refresh tokens.
- Redis.
- Token rotation.
- Logout.
- JWT blacklist.
- `/auth/me`.
- Rate limiting.
- Flyway migrations.

## Recommended Next Learning Block

The auth-service, user-service, PostgreSQL databases, containers, and API gateway are
connected and ready for frontend integration.

The next small learning task should be one of:

1. Connect the Angular registration and login forms to port 8080.
2. Add focused controller and JWT tests.
3. Add Flyway migrations before adding more tables.
4. Build refresh tokens as a separate later lesson.
