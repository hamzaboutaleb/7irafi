# Analytics Service Documentation

**Service Owner:** Yasser Khoussi (7irafi)
**Module Target:** Advanced Analytics Dashboard (Data and Analytics - Major)

## 1. Architectural Overview & Technical Choices

This microservice implements the **CQRS (Command Query Responsibility Segregation)** and **Event-Driven** architectural patterns to fulfill the Advanced Analytics Dashboard requirements without compromising the performance of the core transactional services.

**Why this architecture was chosen:**

* **Database Isolation:** Microservices must own their data. Directly querying the `job-service` or `application-service` PostgreSQL databases for heavy analytics violates service boundaries and degrades online transaction processing (OLTP) performance.
* **Read-Optimized (OLAP) Data:** The dashboard requires filtering thousands of records by date, skill, city, and status instantly. By consuming events, this service builds a "flat" database schema explicitly optimized for these read-heavy queries.


* **Real-Time Responsiveness:** Because this service natively listens for asynchronous data changes via a message broker, it acts as the perfect trigger for the WebSocket invalidation messages required to meet the 5-second visible update target.



## 2. Database Schema

The database uses flat tables specifically designed for time-series aggregation and filtering. Foreign keys and relational constraints are intentionally omitted to maximize read speed.

### Table: `analytics_jobs`

Stores denormalized job data for the "My posted jobs" view.

* `job_id` (UUID, Primary Key)
* `client_id` (UUID, Indexed)
* `status` (VARCHAR, Indexed)
* `skill` (VARCHAR, Indexed)
* `city` (VARCHAR, Indexed)
* `created_at` (TIMESTAMPTZ, Indexed)

### Table: `analytics_applications`

Stores denormalized application data for the "My applications" view.

* `application_id` (UUID, Primary Key)
* `job_id` (UUID)
* `applicant_id` (UUID, Indexed)
* `job_skill` (VARCHAR, Indexed)
* `job_city` (VARCHAR, Indexed)
* `status` (VARCHAR, Indexed) - *Derived status (e.g., ACCEPTED, REJECTED, PENDING)*

* `submitted_at` (TIMESTAMPTZ, Indexed)

*Note: Time-series aggregations explicitly utilize PostgreSQL's `DATE_TRUNC` combined with `AT TIME ZONE 'Africa/Casablanca'` to strictly enforce the reporting timezone requirement*.

## 3. Real-Time Invalidation

To satisfy the automatic update requirement across sessions, this service utilizes Spring WebSockets with STOMP.

* **Connection Endpoint:** `/ws/analytics`
* **Private Destination:** `/queue/analytics-{userId}`
* **Flow:**
1. The message broker (or internal mock API) delivers a data change event to the service.
2. The service saves the flat data to PostgreSQL.
3. The service immediately publishes an invalidation payload (containing `eventId`, `resource`, and `occurredAt`) to the user's specific WebSocket destination.


4. The frontend receives the payload and seamlessly refetches the HTTP aggregates.





## 4. API Endpoints

### Frontend Read APIs

* `GET /analytics/my/jobs` - Returns the aggregated metrics, time-series activity buckets, status distribution, and paginated records for the caller's jobs.


* `GET /analytics/my/applications` - Returns the aggregated metrics, time-series activity buckets, status distribution, and paginated records for the caller's applications.



**Query Parameters:** `from`, `to`, `granularity` (day|week|month), `status` (optional), `skill` (optional), `city` (optional), `page`, `size`.

### Internal Event API (Development / Mocking)

* `POST /internal/mock-events/jobs`
* `POST /internal/mock-events/applications`
*Purpose:* Used to simulate Kafka/RabbitMQ events during local development. These endpoints accept the exact JSON contract agreed upon with the Job and Application service owners and instantly trigger the WebSocket flow. They will be replaced by `@RabbitListener` or `@KafkaListener` annotations once the team infrastructure is complete.