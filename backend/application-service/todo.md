# Job applications — minimum MVP TODO

Reviewed against the repository on 2026-09-16. This is the job application
module (`backend/application-service`); there is no separate `jobapplication`
directory. This review inspected code and documentation, not a running deployment.

## MVP boundary

A worker with an active professional profile applies to an open job, the owner sees who applied and selects
one person, and applicants can see the outcome. Reuse the existing job lifecycle
and four application endpoints. Keep applications as a small table; job-service
remains the source of truth for assignment and status.

## Already implemented — do not rebuild

- [x] `POST /applications`: validate job existence and openness, reject applying
  to your own job, and prevent duplicate applications with a database constraint.
- [x] `GET /applications/my`: current user's paginated applications.
- [x] `GET /applications/job/{jobId}`: paginated applicants, restricted to the owner.
- [x] `PATCH /applications/{applicationId}/accept`: owner-only assignment through
  job-service, with retry support for an already selected applicant.
- [x] Derived `PENDING`, `ACCEPTED`, `NOT_SELECTED`, and `CLOSED` statuses. Missing
  jobs close personal-list entries; upstream failures return `503`.
- [x] Gateway authentication/identity forwarding, service gateway-key protection,
  internal job API key, HTTP timeouts, migration, Docker and database wiring.
- [x] Job-service optimistic locking and same-worker assignment retry behavior.

These checkmarks describe code present, not end-to-end verification.

## Required to finish the MVP

### 1. Wire the application flow into Angular

- [x] Add application response/page types and a small HTTP service for the four
  existing endpoints. Use the current authentication interceptor and the actual
  flat application error shape (`code`, `message`), not the user-service wrapper.
- [x] Connect the job detail modal's disabled **Apply to job** button to POST.
  Hide/disable it for the owner and non-open jobs; disable while submitting and
  show success. Treat `DUPLICATE_APPLICATION` as "Already applied", including
  after a reload. Show actionable errors for closed/missing jobs and outages.
- [x] Add a simple **My applications** list with pagination, job title/detail
  access, submission date and derived status. Reuse job detail reads for labels;
  keep cancelled/deleted entries readable when job detail is unavailable.
- [x] Add an owner entry point using the existing `GET /jobs/my`, then an
  applicants list for a selected job. Provide **Accept**, prevent double clicks,
  and refresh the job/applicant state after success or `409`. A timeout must
  allow retrying the same application because assignment may have succeeded.
- [x] Include loading, empty and retry states for both lists. Reuse existing
  pages/components; a new dashboard design is unnecessary.

Implemented in `frontend/src/app/services/application.ts`, the job detail modal,
`/applications` (personal applications and posted jobs), and
`/jobs/:jobId/applicants`. Navigation is available from Jobs and the dashboard.
Duplicate submissions after a reload are recognized from the server response;
opening a job from My applications also preserves its applied state.

### 2. Make applicants identifiable

- [ ] Provide a minimal authorized lookup of applicant display names by user ID
  in user-service and show those names in the owner list. Current application
  responses contain UUIDs only; `/users/me` cannot resolve another applicant and
  the professional directory has no exact user-ID lookup. Return only the public
  fields needed to choose a person; do not expose private contact data by default.

Keep identity data in user-service. A small lookup is enough; no copied profiles,
ratings system or aggregation service is needed for this flow.

### 3. Worker eligibility implemented

- [x] Only users with an `ACTIVE` professional profile can submit applications.
  Application-service checks user-service using the gateway-authenticated user ID.
  Missing, incomplete and suspended profiles receive `403 WORKER_REQUIRED`;
  verification failures receive `503 USER_SERVICE_UNAVAILABLE` and do not save.
- [x] Keep `USER`/`ADMIN` JWT roles unchanged. Worker eligibility is current profile
  state, not a new token claim; completing a profile needs no new login token.
- [x] In the Apply button flow, explain `WORKER_REQUIRED` and link to
  professional-profile onboarding. The backend remains authoritative.

This supersedes the draft PDF's role-promotion description for the MVP. The
referenced standalone application specification is absent from the repository.

### 4. Verify the rules already implemented

- [ ] Add service/controller tests for successful apply, own/closed/missing job,
  duplicates, invalid UUID/body, pagination bounds, and personal-list isolation.
- [ ] Cover owner-only listing/acceptance, missing application, each derived
  status, same-applicant retries, competing acceptance, and job-service failures.
- [ ] Test `JobClient` against stub HTTP responses, including timeouts and invalid
  summaries, to confirm the documented `404`/`409`/`503` mapping.
- [ ] Verify concurrent duplicate submissions and competing assignments against
  PostgreSQL: one application per user/job, one selected worker, controlled `409`
  for the loser. Existing H2 uniqueness and mocked job-service tests do not prove
  these complete concurrent request paths.
- [x] Add focused frontend tests for apply, duplicate feedback, owner acceptance
  and refreshed status, plus personal-list missing-job handling.
- [ ] Smoke-test through the gateway with an owner and two
  applicants: apply → list → accept → selected/other outcomes. Verify an unrelated
  user cannot list/accept, and forged identity headers cannot change the caller.
- [x] Run the affected Maven suites and frontend tests/build; record results.
  Application tests now also cover worker eligibility and its HTTP client; the
  broader application lifecycle/controller coverage above remains to be added.

## Worker eligibility verification

After implementing the eligibility check, `mvn test` passed in Java 21 Docker
containers: application-service **12 tests**, user-service **44 tests**, no
failures or errors. The full browser/gateway smoke test remains pending.

## Frontend verification

`npm run build` and `npm test -- --watch=false` passed in the frontend Docker
image: **19 tests**, no failures. Applicant display names still require the lookup
in item 2. No live multi-user browser/gateway smoke test was performed.

## Deliberately deferred

No rejection/withdrawal endpoint, proposal text, bidding, attachments, application
counts on job cards, notifications, Kafka events, realtime updates, stored status
workflow, bulk enrichment, chat service, payments, reviews or worker reassignment
is required for this application MVP. Unselected applicants already derive
`NOT_SELECTED` once someone is accepted. A real-world contact/handoff flow is a
separate marketplace concern to settle before a public launch, not a reason to
build chat inside application-service.

## References

- [Application API and runtime contract](README.md)
- [Job architecture and lifecycle](../../docs/development/job-service-architecture.md)
- [Draft job specification (roles and application boundary)](../../docs/specs/Job-service_Specification_v1.0.pdf)
- [Application business rules](src/main/java/com/herafi/application_service/service/JobApplicationService.java)
- [Existing application tests](src/test/java/com/herafi/application_service/)

Done means the browser flow works through the gateway, the owner can identify and
select an applicant, applicants see the correct result, and the checks above pass.
