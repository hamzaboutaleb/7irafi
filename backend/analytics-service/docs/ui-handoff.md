# Analytics UI handoff

Verified on 2026-11-11 against backend commit `55d2419`. This document records the
implementation and open questions; it does not define the dashboard product.
Recheck branch/PR status and changed files at the start of the next session.

## Start here

The user wants to begin the UI work in a separate coding session. The number of
dashboards, their audiences, navigation and contents are **not decided**. Discuss
those with the user before designing screens. Two backend resources do not imply
two dashboards, two user roles, two routes, or two tabs.

- Repository: `/home/yasser/7irafi`, GitHub `hamzaboutaleb/7irafi`.
- Backend branch: `feat/dashboard`.
- Backend PR: [#33](https://github.com/hamzaboutaleb/7irafi/pull/33), open against
  `main` when checked. Latest implementation commit: `55d2419`.
- The working tree was clean before this handoff was added.
- If the PR is still open, create a separate UI branch from its current head and
  target `feat/dashboard` with the UI PR. If merged, branch from updated `main`.
  Do not mix UI changes into the backend PR by default.

## User constraints and corrections to earlier assumptions

1. Prefer straightforward, readable code and established framework features.
   Avoid generic dispatch flags, nested fallback chains and speculative abstraction.
2. Required configuration must fail clearly when absent or blank. Never conceal a
   missing secret/endpoint with a guessed value. An API error is not an empty dataset
   or a zero metric. Document intentional optional behavior explicitly.
3. Job-service and application-service source code remain outside this work's scope.
   Analytics consumes their public events; do not query their databases or change
   producer contracts to make a proposed widget work. Raise a real contract gap.
4. The environment is development-only. Complete pre-existing history was not
   required; simulation through public APIs is acceptable. Do not invent a backfill.
5. Deleted jobs disappear from job analytics. Applications remain governed by their
   own events; deleting a job does not remove its application projections.
6. The agreed live mechanism is **STOMP over WebSocket carrying invalidations**.
   An invalidation is a message meaning “refetch”; it is not a tiny WebSocket,
   a webhook, or an alternative transport. SSE was discussed but not implemented.
7. Flyway was never removed. The implementation uses Spring JDBC and Flyway.
   [The JPA comparison](jdbc-vs-jpa.md) contains illustrative, uncompiled alternatives,
   not an approved ORM migration or a runtime feature switch.
8. Tests used Java **21.0.2**. PostgreSQL **17** was the database container, not the
   Java version. Analytics targets Java 21; its POM still pins Spring Boot 3.2.0
   and Spring Cloud 2023.0.5. Do not assume all services share framework versions
   or silently include a dependency migration in UI work.
9. “No UI in the analytics PR” does **not** mean there is no existing dashboard.
   UI code arrived from `main`; its current design is not new product authorization.

## Existing frontend baseline

The frontend declares Angular 21, TypeScript 5.9, RxJS 7.8, Tailwind 4 and
`@stomp/stompjs` 7.3. Check `package-lock.json` for resolved versions.
It uses standalone Angular components, signals and typed translations in English,
French and Arabic, including RTL. Reuse the existing visual and localization
conventions once the user has agreed the product direction.

Read these files before planning replacements:

| File, relative to repository root | What exists |
| --- | --- |
| `frontend/src/app/app.routes.ts` | Authenticated `/dashboard` route, plus jobs, applications, profiles and conversations routes. |
| `frontend/src/app/pages/dashboard/dashboard.ts` and `.html` | One page with `hiring` / `working` views, profile/onboarding state, previews and review actions. The selection is persisted per user. |
| `frontend/src/app/services/dashboard.ts` | `DashboardData` builds previews through job/application/rating APIs, including extra detail requests and partial-result handling. It does not call analytics. |
| `frontend/src/app/components/dashboard-sidebar/` | Notification activity and reputation. Notification “activity” is different from analytics time buckets. |
| `frontend/src/app/services/notification.ts` and `chat.ts` | Existing STOMP clients; useful context, not implementations to copy without review. They include endpoint/token fallbacks. |
| `frontend/src/app/services/auth.ts` | Token stored in sessionStorage per browser tab; `getToken`, `currentUserId`, login/logout. |
| `frontend/src/app/interceptors/` and `app.config.ts` | HTTP bearer attachment and base URL handling. |
| `frontend/src/environments/` | Development API at `http://localhost:8080`; existing chat/notification socket URLs. No analytics socket setting yet. |
| `frontend/src/app/i18n/` | Existing typed translation keys and language direction. |

The current frontend is not just mock data. It calls operational APIs. The working
preview uses a recent application page, not an account-wide total. Do not relabel
preview lengths as statistics. Analytics cannot replace job detail, applicant,
rating, notification or action APIs wholesale.

The HTTP interceptors recognize API paths **without a leading slash**:
`http.get('analytics/my/jobs', ...)` gets the base URL and bearer token. A leading
`/analytics/...` or an absolute URL bypasses that recognition. Verify this when
adding the client rather than duplicating authentication logic.

## Verified HTTP contract

Browser requests go through the gateway with the existing bearer token. The
gateway forwards trusted identity/shared-secret headers. The UI must never hold
the gateway secret or manufacture `X-User-Id` / `X-Gateway-Key` headers.

| Endpoint | Whose data? | Status values |
| --- | --- | --- |
| `GET /analytics/my/jobs` | Jobs whose `client_id` is the authenticated user. | `OPEN`, `ASSIGNED`, `COMPLETED`, `CANCELLED` |
| `GET /analytics/my/applications` | Applications whose `applicant_id` is the authenticated user. | `PENDING`, `ACCEPTED`, `REJECTED` |

These are owner-scoped queries, not a role-specific dashboard policy. Applications
here are the user's own submissions, **not applicants to the user's posted jobs**.

Both endpoints return the object directly, without a `data` wrapper:

```ts
interface AnalyticsResponse<T> {
  total: number;
  statusDistribution: { value: string; count: number }[];
  activity: { value: string; count: number }[];
  records: T[];
  page: number;
  size: number;
}

interface AnalyticsJob {
  job_id: string;
  client_id: string;
  status: 'OPEN' | 'ASSIGNED' | 'COMPLETED' | 'CANCELLED';
  skill: string;
  city: string;
  created_at: string; // ISO instant
}

interface AnalyticsApplication {
  application_id: string;
  job_id: string;
  applicant_id: string;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED';
  job_skill: string;
  job_city: string;
  submitted_at: string; // ISO instant
}
```

The envelope uses camelCase; records use snake_case. These types document the
contract and have not yet been added to the frontend. There is no `totalPages` or
Spring `Page.content` field. `total` counts the entire filtered dataset, not this
page's records. An out-of-range page may have no records while `total` is positive.

| Query parameter | Implemented behavior |
| --- | --- |
| `from` / `to` | ISO instants, start inclusive / end exclusive, `from < to`. Defaults `1970-01-01T00:00:00Z` / `9999-01-01T00:00:00Z`. |
| `granularity` | Exact lowercase `day`, `week`, `month`; default `day`. |
| `status` | Exact uppercase enum for the selected endpoint. |
| `skill` / `city` | Optional exact case-sensitive matches; nonblank, at most 100 characters. For applications, match `job_skill` / `job_city`. |
| `page` / `size` | Zero-based page >= 0; size 1–20; defaults 0 / 10. |

Totals, distribution, buckets and records all use the same filters and database
snapshot. A status filter also filters the distribution. Invalid inputs produce
HTTP 400; Bean Validation errors include `errors: [{field, message}]` in a Problem
Detail response. Other invalid input errors need not have that extra array.

## What the numbers actually mean

- These are **current snapshots**, not an event history. Distribution shows current
  statuses. Activity counts snapshots by original job creation/application submission
  time, **not** when a status changed or when Kafka processed the event.
- A status change can move a count between statuses for an old creation bucket.
  Deleting a job also removes it from old job buckets and totals. There is no
  historical “number of status changes that day” or deletion counter endpoint.
- Buckets use `Africa/Casablanca`; weeks begin Monday. Values are local date-time
  labels without an offset, e.g. `2026-05-01T00:00`. Do not append `Z` or parse them
  as the browser's local timezone. Casablanca's offset changes; do not hardcode +01.
- Time filters are instants. If the UI offers Casablanca calendar-date ranges,
  convert each boundary using the timezone's rules, including the exclusive end.
- Empty time buckets and absent statuses are omitted, not returned as zero rows.
  Any chart gap-filling must use a defined range and timezone; a display decision
  is still needed. Ordering: activity ascending; records newest first, then ID DESC.
- No job title, description, budget, assigned worker, earnings, ratings or applicant
  counts are in these responses. No conversion-rate or comparison-period endpoint
  exists. Do not invent these fields or confuse accepted applications with completed work.
- The consumer starts at the earliest retained event. Local Kafka retention is seven
  days; previously consumed projections persist beyond that. The seven days are not
  a rolling HTTP filter, and an unfiltered total is not guaranteed lifetime history.

## Live updates and authentication

- Local endpoint: `ws://localhost:8087/ws/analytics`, connected directly to analytics.
  The gateway HTTP route does not establish WebSocket forwarding.
- Native STOMP over WebSocket; no SockJS endpoint is configured.
- Supply `Authorization: Bearer <access token>` in the **STOMP CONNECT frame**.
  Angular HTTP interceptors do not authenticate WebSocket/STOMP traffic.
- Subscribe only to `/user/queue/analytics`. The verified token subject identifies
  the user; do not put a user ID in the subscription path. Client SEND is rejected.
- Message: `{ "eventId": "uuid", "resource": "jobs" | "applications", "occurredAt": "ISO instant" }`.
  It contains neither a record patch nor an aggregate version. `occurredAt` is the
  producer timestamp, not a delivery acknowledgement or safe monotonic ordering key.
- Subscribe and fetch authoritative HTTP state on connection/reconnection; refetch
  relevant visible data on invalidation, preserving current filters. Prevent stale
  overlapping responses from replacing newer filter/account state.
- Clean up subscriptions/connections on logout, account change and the selected
  client lifecycle. Reconnect with the current token; never silently use an empty one.
- Notification delivery is best effort after commit. It can be missed, including
  while a connection remains open. Reconnect refetch does not provide guaranteed
  continuous synchronization; discuss periodic reconciliation/staleness UX if needed.
- One analytics instance is supported for local use. Scaling needs shared delivery.
  The five-second target depends on healthy producers/Kafka and consumer lag.

Deployment work still needed for UI integration: an explicit analytics socket URL
for each environment and, for hosted same-origin use, an actual WebSocket reverse
proxy route. Do not invent a path because chat/notifications use one. The configured
allowed origin must match the frontend; Compose currently sets `http://localhost:4200`.

## Open product questions — ask, do not pre-answer

1. How should analytics relate to the existing dashboard: extend it, replace part
   of it, or have a separate destination? How many views/routes, if any?
2. Who uses each view, and can one person use both hiring and working functions?
   Existing view names and profile states are context, not a new authorization model.
3. Which decisions should each screen help with? Which existing previews/actions,
   notification activity and reputation sections should remain?
4. Which metrics, time range, chart/table presentations and filter relationships
   are wanted? Are current snapshots sufficient for those metrics?
5. What should users see for no history, loading, query failure, disconnected live
   updates, partial operational data and stale data? What refresh behavior is wanted?

Resolve these with the user before a page architecture or widget list is presented
as settled. Explain a backend gap when a requested metric cannot be computed.

## Setup and validation evidence

Follow [the service README](../README.md) for Compose, database initialization,
simulation and tests. Required analytics settings have no runtime fallbacks;
Compose requires shared credentials including monitoring credentials even for a
subset of services. Copy/fill `.env.example`; never commit real `.env` values.
Existing PostgreSQL volumes keep their existing password.

`simulate.py` creates fresh users, jobs and applications through public APIs, then
cancels/deletes and polls analytics. It is a development aid, not a historical-data
loader. Wait for service discovery before using it.

Latest executed verification at `55d2419`: 66 analytics tests passed with Java 21,
PostgreSQL 17 and embedded Kafka; 16 gateway tests passed. Compose accepted valid
configuration and rejected all 15 required credentials when missing or empty.
The earlier implementation exercised the full simulation and live STOMP delivery;
the full stack was not rerun after the final configuration/refactoring pass.
There is no verified analytics UI integration yet. No frontend build/tests were run
for this documentation-only handoff.

For the future UI change, verify its agreed behavior, especially real response
shapes, user isolation/account changes, filter pagination, timezone boundaries,
reconnect/refetch, teardown, and visible error states. Use the repository's actual
build/test commands and report what ran rather than inheriting backend test claims.

## Copy into the next session

> Read `backend/analytics-service/docs/ui-handoff.md` and inspect the current branch
> and relevant source. I want to work on the analytics UI, but dashboard count,
> organization, audiences and content are not decided. First help me clarify those
> using the existing UI and verified backend capabilities. Do not treat the existing
> hiring/working switch or two API endpoints as my product decision. Keep the code
> straightforward, do not hide errors with defaults, and do not change job-service
> or application-service. Once we agree the UI scope, implement it on a separate
> branch stacked on backend PR #33 if that PR is still open.
