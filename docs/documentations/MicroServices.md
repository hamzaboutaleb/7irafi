

---

# 1. What is a microservice?

A **microservice** is a small backend application that is responsible for one clear business capability.

Example business capabilities:

- Authentication
- User profiles
- Job posting
- Applications
- Reviews
- Payments
- Notifications

A microservice usually has:

- Its own codebase or module
- Its own runtime process
- Its own API
- Its own database or private schema
- Its own deployment pipeline
- Its own logs and health checks
- Its own configuration
- Its own versioned contract

A microservice should be independently deployable. That means you can update `job-service` without rebuilding and redeploying `review-service`, `auth-service`, and `application-service`.

A microservice is not defined by size only. A service can be small and still badly designed. The important part is **independence around a business capability**.

---

# 2. Must-Have Properties to Be Called a Microservice

Not every small service qualifies. A true microservice must have ALL of these:

### ✅ 1. Single Responsibility
Each service does **one thing** and does it well.

A rule of thumb: if you can't describe what the service does in one sentence without using "and", it's probably doing too much.

### ✅ 2. Independent Deployability
You must be able to deploy Service A **without touching** Service B, C, or D.

### ✅ 3. Owns Its Own Data
Each microservice has its **own dedicated database**. No two services share a database or directly query each other's tables (more in Chapter 4).

#### Why? The 5 Reasons

#### 🔴 Reason 1: Independent Deployability
If OrderService and UserService share a database, a schema change in the users table (to fix a UserService bug) might **break OrderService** — even though OrderService wasn't touched.

#### 🔴 Reason 2: Fault Isolation
If OrderService does a runaway query that locks the shared database, it kills UserService and PaymentService too.

#### 🔴 Reason 3: Technology Freedom
With separate databases, each service can pick the best tool:

```
UserService     ──► PostgreSQL  (relational, ACID transactions)
ProductCatalog  ──► MongoDB     (flexible documents for product attributes)
SearchService   ──► Elasticsearch (full-text search)
CartService     ──► Redis        (fast in-memory for session data)
AnalyticsService ──► Cassandra  (time-series, write-heavy)
```

#### 🔴 Reason 4: Independent Scaling
You can scale the service AND its database independently.

```
During Black Friday:
  OrderService  ──► scaled to 20 instances ──► orders_db (larger RDS instance)
  UserService   ──► stays at 2 instances   ──► users_db  (small, unchanged)
```

#### But What About Joins?

With one DB per service, you can't do SQL `JOIN` across services. This is a real tradeoff.

```
Question: "Show me all orders with the customer's name for order #123"

Old (monolith):
  SELECT o.*, u.name FROM orders o JOIN users u ON o.user_id = u.id
  WHERE o.id = 123

New (microservices):
  Step 1: OrderService gets order #123  → { orderId: 123, userId: 456, ... }
  Step 2: UserService gets user #456    → { userId: 456, name: "Alice", ... }
  Step 3: Combine in API layer or in the calling service
```

This is solved by **API composition** (calling both services and combining) or by **event-driven data duplication** (OrderService stores a copy of the user's name when the order is placed, via an event).

### ✅ 4. Communicates via Well-Defined APIs
Services talk to each other only through network APIs (REST, gRPC, or messaging). No direct method calls, no shared memory, no shared libraries with business logic.

```
OrderService needs a user's email:
  ✅ Calls UserService's REST API: GET /users/{id}
  ❌ Never: directly imports UserService's Java class
  ❌ Never: reads from UserService's database table
```

### ✅ 5. Decentralized Technology
Each service can use a **different programming language, framework, or database** if that's what makes sense.

```
UserService     ──► Java + Spring Boot + PostgreSQL
RecommendationService ──► Python + FastAPI + MongoDB
NotificationService  ──► Node.js + Express + Redis
```

### ✅ 6. Designed Around Business Capabilities (not technical layers)
Traditional layers split apps by technical concern. Microservices split by **business domain**.

```
❌ WRONG (technical layers):
  PresentationLayer / BusinessLogicLayer / DataAccessLayer

✅ RIGHT (business capabilities):
  UserService / OrderService / InventoryService / PaymentService
```

### ✅ 7. Independently Scalable
You can run 10 copies of PaymentService and 1 copy of UserService if payments is the bottleneck.

```
       Users ──► [Gateway]
                    │
          ┌─────────┼─────────┐
          │         │         │
    [UserSvc x1] [OrderSvc x1] [PaymentSvc x10]
                               ↑
                         High load here!
                         Scale THIS service only
```

### ✅ 8 .Good service boundary questions

Ask:

1. Does this service own a business concept?
2. Can it make decisions without constantly asking other services?
3. Can it own its own database?
4. Can it be deployed independently?
5. Can its API be described clearly?
6. Would a real team own this service?

If the answer is no, the boundary is probably wrong.

---


# 3. Domain-Driven Design (DDD)

### What Is DDD?

**Domain-Driven Design** (DDD) is a methodology for designing software by deeply understanding the **business domain** (the problem space) and structuring the code around that understanding.

It was created by **Eric Evans** in his 2003 book "Domain-Driven Design." It's not a framework — it's a set of patterns and principles.

### Why DDD Matters for Microservices

DDD answers the hardest question in microservices: **"How do I decide what goes in each service?"**

Without DDD guidance, teams split services by technology, team size, or gut feeling — leading to poorly bounded services that need to call each other constantly.

### Core DDD Concepts

#### 🔷 Domain
The **domain** is the subject area of the business. For an e-commerce app: shopping, ordering, payment, shipping, inventory are all parts of the domain.

#### 🔷 Bounded Context
A **Bounded Context** is a clear boundary within which a particular domain model applies. This is the **key concept** that maps to microservices.

```
┌──────────────────────────────────────────────────┐
│            E-COMMERCE SYSTEM                     │
│                                                  │
│  ┌──────────────────┐    ┌───────────────────┐   │
│  │  SALES CONTEXT   │    │ INVENTORY CONTEXT │   │
│  │                  │    │                   │   │
│  │ "Product" means: │    │ "Product" means:  │   │
│  │ - Name           │    │ - SKU             │   │
│  │ - Price          │    │ - Warehouse loc   │   │
│  │ - Description    │    │ - Stock quantity  │   │
│  └──────────────────┘    └───────────────────┘   │
│                                                  │
│  ┌──────────────────┐    ┌───────────────────┐   │
│  │ PAYMENT CONTEXT  │    │ SHIPPING CONTEXT  │   │
│  │                  │    │                   │   │
│  │ "Order" means:   │    │ "Order" means:    │   │
│  │ - Total amount   │    │ - Delivery addr   │   │
│  │ - Payment method │    │ - Weight          │   │
│  │ - Billing addr   │    │ - Tracking num    │   │
│  └──────────────────┘    └───────────────────┘   │
└──────────────────────────────────────────────────┘
```

Notice: "Product" and "Order" mean **different things** in different contexts. DDD says this is **fine and expected** — each bounded context has its own model.

**Each Bounded Context = One Microservice** (roughly). This is how DDD guides microservice boundaries.


You never directly modify an `OrderItem` — you go through `Order`. This protects business rules.

#### 🔷 Domain Events
Something significant that happened in the domain. Events are named in past tense.

```
OrderPlaced       ─► triggers InventoryReserved
InventoryReserved ─► triggers PaymentRequested
PaymentCompleted  ─► triggers OrderConfirmed
OrderConfirmed    ─► triggers ShipmentScheduled
```

These domain events are what flow between microservices. This is how services stay decoupled.

---

# 4. API Gateway

### What Is an API Gateway?

An **API Gateway** is a single entry point that sits in front of all your microservices. External clients (browsers, mobile apps, third-party systems) talk to the gateway. The gateway routes those requests to the correct internal service.


### What Does a Gateway Do?

#### 🔧 1. Request Routing
The gateway reads the URL path and routes to the correct service.

```
/api/users/**     ──► user-service
/api/orders/**    ──► order-service
/api/payments/**  ──► payment-service
```

#### 🔧 2. Authentication & Authorization
The gateway validates JWT tokens before forwarding requests. Services don't need to do auth themselves.

#### 🔧 3. Rate Limiting
Prevent abuse by limiting how many requests a client can make per second/minute.

#### 🔧 4. Load Balancing
If you have 3 instances of OrderService, the gateway distributes traffic across them.

#### 🔧 5. SSL/TLS Termination
The gateway handles HTTPS. Internal communication can be plain HTTP (within a private VPC where it's safe).

#### 🔧 6. Request/Response Transformation
Rewrite requests before forwarding (e.g., strip a path prefix, add headers).

#### 🔧 7. CORS (Cross-Origin Resource Sharing)
Handle browser CORS headers in one place rather than in every service.

#### 🔧 8. Circuit Breaking
Stop routing to a service that is failing 


### Gateway technologies

Common API gateway technologies:

- Spring Cloud Gateway
- NGINX
- Traefik
- Kong Gateway
- Envoy Proxy
- AWS API Gateway
- Azure API Management
- Google Cloud API Gateway

For a Java **Spring Cloud Gateway** is the most natural choice.

---

# 5. Spring Cloud 

### What Is Spring Cloud?

**Spring Cloud** is a collection of tools and libraries built on top of Spring Boot that solve common microservices challenges:

```
Spring Cloud provides:
├── Spring Cloud Gateway          ← API Gateway
├── Spring Cloud Netflix Eureka  ← Service Discovery
├── Spring Cloud Config           ← Centralized Configuration
├── Spring Cloud OpenFeign        ← Declarative HTTP client
├── Spring Cloud LoadBalancer     ← Client-side load balancing
├── Spring Cloud Sleuth           ← Distributed tracing (deprecated → Micrometer)
└── Spring Cloud Circuit Breaker  ← Circuit breaking (Resilience4j integration)
```

### The Full Spring Cloud Architecture

```
                          ┌──────────────────┐
                          │  CONFIG SERVER   │ ← serves configs
                          │  :8888           │
                          └────────┬─────────┘
                                   │ (all services fetch config on startup)
                                   │
┌──────────────────┐      ┌────────▼─────────┐
│  EUREKA SERVER   │      │                  │
│  (Discovery)     │◄─────┤   ALL SERVICES   │
│  :8761           │      │   register here  │
└──────────────────┘      └────────┬─────────┘
                                   │
                          ┌────────▼─────────┐
External                  │   API GATEWAY    │
Client ──────────────────►│   :8080          │
                          └────────┬─────────┘
                                   │ routes to...
                    ┌──────────────┼──────────────┐
                    │              │              │
             ┌──────▼───┐  ┌──────▼───┐  ┌──────▼───┐
             │  USER    │  │  ORDER   │  │ PAYMENT  │
             │  SERVICE │  │  SERVICE │  │  SERVICE │
             │  :8081   │  │  :8082   │  │  :8083   │
             └──────────┘  └──────────┘  └──────────┘
```


# 6. Config Server

### What Is It and Why Do We Need It?

In a microservices system with 20 services, each service has its own `application.yml` with configurations:
- Database URLs
- External API keys
- Feature flags
- Timeout values
- Kafka broker addresses

**Problems with local config files:**
- You have 20 `application.yml` files across 20 repos
- Changing the Kafka broker URL means updating 20 files and redeploying 20 services
- Secrets (passwords, API keys) are checked into Git — a security nightmare
- Different environments (dev, staging, prod) need different values

**Config Server solves this:**


### Setting Up Config Server

```
config-repo/ (separate Git repo)
├── application.yml          ← Global config for ALL services
├── application-dev.yml      ← Global config, dev environment
├── application-prod.yml     ← Global config, production
├── user-service.yml         ← user-service specific
├── user-service-dev.yml
├── order-service.yml
└── order-service-prod.yml
```

### Do you always need Config Server?

No.

For 5 services in Docker Compose, environment variables can be enough.

But if your project is specifically about microservices, adding Config Server shows that you understand distributed configuration.

---

# 7. Discovery Server

### What Is Service Discovery?

In a microservices system, services need to find each other. But services can:
- Run on different hosts
- Change IP addresses (containers get new IPs on restart)
- Have multiple instances (10 instances of PaymentService — which IP to use?)

**Service Discovery** is the mechanism by which services find each other dynamically.


## Common technologies

- Eureka
- Consul
- Kubernetes Service DNS
- Zookeeper, older/common in some systems
- Docker Compose DNS, simple local discovery


### How Eureka Works (Step by Step)

```
                      ┌─────────────────┐
                      │  EUREKA SERVER  │
                      │  (Registry)     │
                      │                 │
                      │ Registry:       │
                      │ USER-SERVICE    │
                      │  → 10.0.0.1:80 │
                      │  → 10.0.0.2:80 │
                      │ ORDER-SERVICE   │
                      │  → 10.0.0.5:80 │
                      └────────┬────────┘
                               │
                ┌──────────────┼──────────────┐
                │              │              │
    ┌───────────▼──┐  ┌────────▼─────┐  ┌────▼──────────┐
    │ USER-SERVICE │  │USER-SERVICE  │  │ORDER-SERVICE  │
    │ 10.0.0.1:80  │  │ 10.0.0.2:80 │  │ 10.0.0.5:80   │
    │              │  │              │  │               │
    │ Registers on │  │ Registers on │  │ Registers on  │
    │ startup:     │  │ startup:     │  │ startup AND   │
    │ "I'm USER-   │  │ "I'm USER-   │  │ looks up:     │
    │  SERVICE     │  │  SERVICE     │  │ "Where is     │
    │  at this IP" │  │  at this IP" │  │  USER-SERVICE?│
    └──────────────┘  └──────────────┘  └───────────────┘

ORDER-SERVICE wants to call USER-SERVICE:
1. Asks Eureka: "Where is USER-SERVICE?"
2. Eureka returns: [10.0.0.1:80, 10.0.0.2:80]
3. ORDER-SERVICE picks one (load balancing, e.g. round-robin)
4. Makes HTTP call directly to that instance
```

### Heartbeats and Health Checks

Every service sends a **heartbeat** (a ping) to Eureka every 30 seconds (configurable).

```
PaymentService ──► Eureka: "Still alive!" (every 30 sec)

If Eureka doesn't receive heartbeat for 90 seconds:
  → Marks PaymentService instance as DOWN
  → Removes it from the registry
  → Other services won't be given this instance anymore

When the service comes back up:
  → Re-registers with Eureka
  → Gets added back to registry
  → Traffic flows to it again
```

### Client-Side vs. Server-Side Load Balancing

**Server-Side**: Gateway/load balancer picks the instance (NGINX, AWS ALB)
**Client-Side**: The calling service itself picks which instance to call (Eureka + Spring Cloud LoadBalancer)


### Do you need Eureka in Docker Compose?

Not strictly.

Docker Compose already gives service DNS names.

Example:

```yaml
services:
  job-service:
    ...
  user-service:
    ...
```

Then `job-service` can call:

```text
http://user-service:8082
```

For a school project, Eureka is optional.

If you want to demonstrate Spring Cloud patterns, you can add it.

---

# 8. Private Network

### Must Microservices Be on a Private Network?

**YES. This is critical for security.**

Services should be on a **private network** (VPC — Virtual Private Cloud or an internal Docker network). Only the API Gateway should be publicly accessible.

```
INTERNET
    │
    │ (public internet traffic)
    ▼
┌───────────────────────────────────────────────────┐
│  PUBLIC SUBNET                                    │
│  ┌─────────────┐                                  │
│  │ API GATEWAY │  ← ONLY publicly accessible      │
│  │ port 443    │                                  │
│  └──────┬──────┘                                  │
└─────────┼─────────────────────────────────────────┘
          │ (internal traffic only)
┌─────────▼─────────────────────────────────────────┐
│  PRIVATE SUBNET (VPC / Docker Network)             │
│                                                   │
│  ┌─────────────┐  ┌─────────────┐  ┌───────────┐  │
│  │USER-SERVICE │  │ORDER-SERVICE│  │PAYMENT-SVC│  │
│  │ port 8081   │  │ port 8082   │  │ port 8083 │  │
│  │ NO public   │  │ NO public   │  │ NO public │  │
│  │ exposure    │  │ exposure    │  │ exposure  │  │
│  └─────────────┘  └─────────────┘  └───────────┘  │
│                                                   │
│  ┌─────────────┐  ┌─────────────┐                 │
│  │ KAFKA       │  │ DATABASES   │                 │
│  │ (private)   │  │ (private)   │                 │
│  └─────────────┘  └─────────────┘                 │
└───────────────────────────────────────────────────┘
```

### Why Private?

- **Security**: PaymentService should never be directly callable from the internet. Only via Gateway (which validates auth).
- **Compliance**: PCI-DSS (payment security standard) requires payment systems to be in isolated networks.
- **Attack surface reduction**: Even if Gateway is compromised, attacker can't directly call internal services... unless they're on the private network too.
- **Performance**: Internal network calls are faster and free (no egress charges in most clouds).



# 9. Sync vs. Async Communication

### Synchronous Communication

The caller **waits** for the response before continuing.

**Technologies**: REST (HTTP), gRPC

**When to use sync:**
- You need the result immediately to continue processing
- Simple request/response
- User is waiting for an answer

**Problems with sync:**
```
Cascading failures:
  Client ──► Gateway ──► OrderService ──► PaymentService ──► BankAPI
  
  BankAPI is slow (3 seconds)
  PaymentService waits 3 seconds
  OrderService waits 3 seconds (for PaymentService)
  Gateway waits 3 seconds (for OrderService)
  Client waits 3 seconds
  
  If BankAPI goes down: PaymentService fails → OrderService fails → Client fails
  All services are coupled in failure!
```

### Asynchronous Communication

The sender **does NOT wait**. It sends a message and moves on. The receiver processes it in its own time.

```
OrderService ──►[Message Queue]
             (publishes event, immediately moves on)
             
                    [Message Queue] ──► PaymentService
                                        (processes payment, later)

OrderService doesn't know when or whether PaymentService processed it.
They are completely decoupled.
```

**Technologies**: Kafka, RabbitMQ, AWS SQS, Azure Service Bus

**When to use async:**
- You don't need the result immediately
- Notifying other services about what happened
- High throughput required
- Fault tolerance is critical

### The Two Async Styles

**Fire-and-Forget**: Producer sends, doesn't care about result
```
OrderService → "OrderPlaced" → [Queue] → NotificationService (sends email)
OrderService doesn't wait for email to be sent. It's irrelevant to order processing.
```

**Request-Reply over Async**: Producer sends, expects reply later (correlation ID)
```
OrderService → "ProcessPayment" + correlationId: "abc123" → [Queue]
              ... does other work ...
PaymentService processes payment
PaymentService → "PaymentResult" + correlationId: "abc123" → [Reply Queue]
OrderService reads reply queue, matches correlationId, processes result
```

### Decision Matrix

```
QUESTION                              SYNC       ASYNC
──────────────────────────────────────────────────────
Need result to continue?              ✓
Non-critical side effect?                        ✓
Multiple consumers?                              ✓
High throughput/burst traffic?                   ✓
Low latency user-facing API?          ✓
Decoupling for resilience?                       ✓
Guaranteed delivery needed?                      ✓
Simple query?                         ✓
Long-running process?                            ✓
```

---

# 10. Producer / Consumer 

### What Are Producer, Consumer and Message?

## Producer

A producer sends messages.

Example:

```text
job-service produces JobCreated event
```

## Consumer

A consumer receives and processes messages.

Example:

```text
notification-service consumes JobCreated event
```

```
ORDER SERVICE          KAFKA/RABBITMQ         NOTIFICATION SERVICE
  (Producer)           (Message Broker)         (Consumer)
      │                      │                       │
      │  "OrderPlaced" event  │                       │
      ├─────────────────────► │                       │
      │                      │  "OrderPlaced" event  │
      │                      ├──────────────────────►│
      │                      │                       │ (sends email)
```

## Message

A message is data sent through a broker.

Example:

```json
{
  "eventId": "e-123",
  "eventType": "JobCreated",
  "version": 1,
  "occurredAt": "2026-06-23T12:00:00Z",
  "data": {
    "jobId": 10,
    "clientId": 5,
    "title": "Fix kitchen sink",
    "city": "Casablanca"
  }
}
```

### Why This Pattern?

Without producer/consumer:
```
OrderService places an order, then SYNCHRONOUSLY calls:
  1. PaymentService.processPayment()     ← must wait
  2. NotificationService.sendEmail()     ← must wait
  3. InventoryService.reserveStock()     ← must wait
  4. AnalyticsService.trackEvent()       ← must wait

Total time = sum of all calls
If NotificationService is down = OrderService fails
If any service is slow = OrderService is slow
```

With producer/consumer:
```
OrderService places an order, then:
  1. Publishes "OrderPlaced" event to Kafka ← fast, fire-and-forget
  2. Returns success to client immediately

Meanwhile (asynchronously, in parallel):
  PaymentService consumes "OrderPlaced" → processes payment
  NotificationService consumes "OrderPlaced" → sends email
  InventoryService consumes "OrderPlaced" → reserves stock
  AnalyticsService consumes "OrderPlaced" → tracks event

OrderService doesn't wait. If NotificationService is down,
the event stays in Kafka until it comes back up.
```

### Multiple Consumers (Pub/Sub)

One event can have **many consumers**. This is called **Publish-Subscribe** (Pub/Sub):

```
OrderService publishes "OrderPlaced" event
                │
                ▼
           [Kafka Topic: order-events]
                │
    ┌───────────┼───────────┐
    │           │           │
    ▼           ▼           ▼
PaymentSvc  NotifSvc   InventorySvc
(consumer)  (consumer)  (consumer)

All three get the same event independently.
```

###  Message Queue

A **message queue** is a system that holds messages (data) between a sender and a receiver. The sender puts messages IN, the receiver takes messages OUT. They don't communicate directly — the queue sits between them.



# 11. Message Brokers

### What Is a Message Broker?

A **message broker** is an intermediary software that enables services to communicate asynchronously. Producers send messages to the broker; consumers receive messages from the broker.

```
Without broker (direct):
  Producer ──► Consumer
  (Producer must know Consumer's address, Consumer must be up)

With broker:
  Producer ──► [Broker] ──► Consumer
  (Producer only knows Broker's address)
  (Consumer pulls from Broker when ready)
  (Both can be up/down independently)
```

### Broker Patterns

**Queue (Point-to-Point)**:
- One message goes to ONE consumer
- If multiple consumers, each message is processed by only one
- Used for: task distribution, work queues

```
Producer ──► [Queue] ──► Consumer A (gets msg 1)
                    ──► Consumer B (gets msg 2)
                    ──► Consumer C (gets msg 3)
Each message processed ONCE by ONE consumer.
```

**Topic (Publish-Subscribe)**:
- One message goes to ALL consumers
- Used for: events, notifications, broadcasting

```
Producer ──► [Topic] ──► Consumer A (gets msg 1 copy)
                    ──► Consumer B (gets msg 1 copy)
                    ──► Consumer C (gets msg 1 copy)
Each consumer gets every message independently.
```

### Popular Message Brokers

```
BROKER        MODEL        BEST FOR                DELIVERY
────────────────────────────────────────────────────────────
Kafka         Log/Topic    High throughput,         At-least-once
                           event streaming,          (configurable)
                           replay

RabbitMQ      Queue+Topic  General messaging,       At-most-once /
                           task queues,              At-least-once
                           flexible routing

AWS SQS       Queue        AWS-native,              At-least-once
                           serverless

AWS SNS       Topic        AWS-native fan-out       At-least-once

Redis Streams  Log         Fast, simple streaming   At-least-once

ActiveMQ      Queue+Topic  Enterprise JMS           Configurable
```

---

# 12. Apache Kafka

### What Is Kafka?

**Apache Kafka** is a distributed event streaming platform. Think of it as a massively scalable, durable **log** that many services can write to and read from.

Originally built by LinkedIn to handle billions of events per day. Now the industry standard for high-throughput event streaming.

### Core Concepts

```
KAFKA ARCHITECTURE:

┌─────────────────────────────────────────────────────────────┐
│                      KAFKA CLUSTER                          │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  TOPIC: "order-events"                               │   │
│  │                                                      │   │
│  │  Partition 0: [msg1][msg2][msg3][msg4]──► consumers  │   │
│  │  Partition 1: [msg5][msg6][msg7]────────► consumers  │   │
│  │  Partition 2: [msg8][msg9]──────────────► consumers  │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  TOPIC: "payment-events"                             │   │
│  │  Partition 0: [...]                                  │   │
│  │  Partition 1: [...]                                  │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  BROKER 1    BROKER 2    BROKER 3   (Kafka cluster nodes)   │
└─────────────────────────────────────────────────────────────┘
```

**Topic**: A named category/feed of messages (like a table in a database, or a folder for files)

**Partition**: A topic is split into partitions for parallelism. Each partition is an ordered, immutable log. Messages within a partition are guaranteed to be in order.

**Offset**: The position of a message within a partition. Consumers track which offset they've read up to.

```
Partition 0:
  Offset 0: {"orderId": 1, "event": "OrderPlaced"}
  Offset 1: {"orderId": 2, "event": "OrderPlaced"}
  Offset 2: {"orderId": 1, "event": "OrderShipped"}
  Offset 3: {"orderId": 3, "event": "OrderPlaced"}
  Current consumer position: offset 2 (has read 0, 1, 2)
```

**Consumer Group**: A group of consumers that cooperate to consume a topic. Each partition is consumed by exactly one consumer in the group.

**Broker**: A single Kafka server. A Kafka cluster has multiple brokers for redundancy.

**Replication**: Each partition is replicated across multiple brokers. If one broker dies, data isn't lost.

### Why Kafka Is Special

**Messages are retained** (default: 7 days). Consumers can:
- **Replay**: re-read messages from the past
- **Start fresh**: start reading from the beginning of the topic
- **New consumer**: a new service can subscribe and read all past events

```
Regular queue: message consumed → message DELETED from queue
Kafka: message consumed → message STILL IN TOPIC for 7 days
  Other consumers can still read it
  New consumers can read from beginning
  Can replay events to rebuild state
```




### Kafka vs. Traditional Queue

```
FEATURE               KAFKA              RABBITMQ/SQS
───────────────────────────────────────────────────────
Message retention     Yes (days/forever) No (deleted after consumed)
Replay messages       Yes                No
Throughput            Very high          High
Ordering              Per partition      Per queue
Multiple consumers    Yes (consumer grp) Yes (competing consumers)
Message routing       Topics             Exchanges + routing keys
Complexity            Higher             Lower
Use case              Event streaming    Task queues, RPC
```

---

# 13. RabbitMQ

### What Is RabbitMQ?

**RabbitMQ** is a traditional message broker implementing the **AMQP** (Advanced Message Queuing Protocol). It's been around since 2007 and is very reliable, battle-tested, and widely used.

### RabbitMQ Core Concepts

```
RABBITMQ ARCHITECTURE:

PRODUCER
  │
  │ publish
  ▼
┌───────────────────────────────────────────────────┐
│  EXCHANGE                                         │
│  (routing logic lives here)                       │
│                                                   │
│  Direct Exchange: route by exact routing key      │
│  Topic Exchange: route by routing key pattern     │
│  Fanout Exchange: route to ALL queues             │
│  Headers Exchange: route by message headers       │
└───────────────────────────────────────────────────┘
  │ binding (exchange to queue connection with routing key)
  ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│   QUEUE A    │   │   QUEUE B    │   │   QUEUE C    │
│              │   │              │   │              │
│ [msg][msg]   │   │ [msg]        │   │ [msg][msg]   │
└──────┬───────┘   └──────┬───────┘   └──────┬───────┘
       │                  │                  │
       ▼                  ▼                  ▼
  CONSUMER A         CONSUMER B         CONSUMER C
```

### Exchange Types

**Direct Exchange**: Routes message to queue where binding key = routing key (exact match)
```
Producer sends: routingKey = "order.payment"
  → Goes to queues bound with "order.payment" exactly
```

**Topic Exchange**: Routes by pattern matching (wildcards)
```
Routing key patterns:
  "order.*"   matches "order.payment", "order.placed" but not "order.placed.v2"
  "order.#"   matches "order.payment", "order.placed.v2", "order.a.b.c"

Producer sends: routingKey = "order.placed"
  → Queues with "order.*" or "order.#" bindings receive it
```

**Fanout Exchange**: Ignores routing key, sends to ALL bound queues
```
Producer sends: (any routing key, ignored)
  → Queue A receives copy
  → Queue B receives copy
  → Queue C receives copy
```


### RabbitMQ vs. Kafka When to Use Which

```
USE RABBITMQ WHEN:                      USE KAFKA WHEN:
───────────────────────────────────────────────────────────
Task queues (one task, one worker)       Event streaming (one event, many consumers)
Complex routing logic needed             Simple topic-based routing is enough
Messages deleted after consumption       Need to replay past events
Low-to-medium throughput               Very high throughput (millions/sec)
Need message priorities                  Need strict ordering per key
RPC pattern                             Event sourcing / CQRS
Quick to set up                         Long-term event log
```

---

# 14. Security

### Security Challenges in Microservices

In a monolith, security is in one place. In microservices:

```
MONOLITH:
  One auth check → protects everything inside
  
MICROSERVICES:
  Gateway → checks token ✓
  
  But... internal services call each other!
  Does OrderService check that the request from InventoryService is legitimate?
  Does InventoryService blindly trust anything coming from OrderService?
  
  What if an attacker bypasses the gateway and calls OrderService directly?
```

### The Security Layers

```
Layer 1: NETWORK SECURITY
  All services on private VPC
  Only gateway exposed publicly
  Services can't be reached from internet

Layer 2: GATEWAY AUTHENTICATION
  All external requests validated at gateway
  JWT token verified before forwarding

Layer 3: SERVICE-TO-SERVICE AUTHENTICATION
  Services verify they're talking to legitimate internal services
  (mTLS or internal JWT)

Layer 4: AUTHORIZATION
  Even authenticated users can only access their own resources
  Role-based or attribute-based access control

Layer 5: DATA ENCRYPTION
  All data encrypted at rest (database encryption)
  All data encrypted in transit (TLS/HTTPS)
```

### JWT (JSON Web Token)

**JWT** is the most common authentication mechanism in microservices.

```
JWT Structure (3 parts separated by dots):

eyJhbGciOiJSUzI1NiJ9         ← Header (base64 encoded)
.
eyJzdWIiOiJ1c2VyMTIzIiwiZW  ← Payload (base64 encoded)
1haWwiOiJhbGljZUBleGFtcGxl
LmNvbSIsInJvbGVzIjpbIlVTRVIi
XSwiZXhwIjoxNzAwMDAwMDAwfQ
.
SflKxwRJSMeKKF2QT4fwpMeJf36P  ← Signature (cryptographic)
Ok6yJV_adQssw5c
```

**Decoded Payload:**
```json
{
  "sub": "user123",              // Subject (user ID)
  "email": "alice@example.com",
  "roles": ["USER", "PREMIUM"],
  "exp": 1700000000,             // Expiration timestamp
  "iat": 1699996400,             // Issued at timestamp
  "iss": "https://auth.company.com"  // Issuer
}
```

The **signature** ensures the token wasn't tampered with. Any service with the public key can verify it.

### JWT Flow

```
1. User logs in:
   Client ──► Auth Service: POST /login { username, password }
   Auth Service validates credentials
   Auth Service creates JWT (signed with private key)
   Auth Service ──► Client: { accessToken: "eyJ...", refreshToken: "..." }

2. Subsequent requests:
   Client ──► Gateway: GET /api/orders
              Authorization: Bearer eyJ...
   
   Gateway: validate JWT signature (using public key)
   If valid: extract user ID and roles, add to headers
   Gateway ──► OrderService: GET /orders
               X-User-Id: user123
               X-User-Roles: USER,PREMIUM

3. OrderService receives request:
   Reads X-User-Id header (trusted — came from Gateway)
   Applies authorization: is this user allowed to see these orders?
```

---

### Centralized Security

Instead of each service implementing its own authentication and authorization logic, a **centralized security service** handles it all. Services delegate security decisions to this central authority.

```
DECENTRALIZED (BAD):
  UserService: has its own JWT validation code
  OrderService: has its own JWT validation code
  PaymentService: has its own JWT validation code
  
  Problem: 20 services → 20 copies of security code
           Update one, forget to update others → inconsistency
           One service has a security bug → vulnerability

CENTRALIZED (GOOD):
  All security in one place:
  ┌──────────────────────────────────────────┐
  │  AUTH/SECURITY SERVICE                   │
  │  - User login / logout                   │
  │  - Token issuance                        │
  │  - Token validation                      │
  │  - Role management                       │
  │  - Permission checking                   │
  └──────────────────────────────────────────┘
  
  Other services just ask:
  "Is this token valid?" → YES/NO
  "Can user X do action Y on resource Z?" → YES/NO
```

### Gateway as First Security Layer

```
REQUEST FLOW WITH CENTRALIZED SECURITY:

Client ──► Gateway
  │
  Gateway: validate JWT
    ├── Invalid token → 401 Unauthorized (request dies here)
    └── Valid token → extract user info
              │
              Gateway ──► Downstream Service
                          (with X-User-Id, X-User-Roles headers)
              
              Downstream Service:
              - Trusts the headers (network-level trust — only gateway can set them)
              - Applies business-level authorization:
                "Can user X read order Y?" (user owns order Y?)
              - No JWT validation needed — gateway already did it
```

`

---

# 15. Keycloak / Auth0 / Auth Providers

### What Is an Auth Provider?

An **auth provider** (also called Identity Provider, IdP) is a dedicated system for authentication and authorization. Instead of building your own login system from scratch (handling passwords, tokens, OAuth flows, 2FA, password resets, etc.), you use a proven, secure, feature-rich auth provider.

```
WITHOUT Auth Provider:
  Your team builds:
  - User registration + password hashing
  - Password reset flow
  - JWT issuance and rotation
  - OAuth2/OIDC server
  - 2FA/MFA
  - Social login (Google, Facebook)
  - Token revocation
  - Session management
  → Months of work, many security risks

WITH Auth Provider:
  Auth provider handles all of the above
  Your services just ask: "Validate this token"
  → Days to integrate
```

### Keycloak

**Keycloak** is an open-source Identity and Access Management (IAM) solution. You self-host it. Feature-rich and free (but you manage the infrastructure).

```
Keycloak Concepts:
  Realm: Isolated authentication domain (e.g., "my-company" realm)
  Client: An application that uses Keycloak for auth (your microservices)
  User: A person with credentials in Keycloak
  Role: A permission level (ADMIN, USER, etc.)
  Group: A collection of users
  
Architecture:
  ┌─────────────────────────────────────┐
  │           KEYCLOAK SERVER            │
  │  ┌─────────────────────────────┐    │
  │  │  my-company Realm           │    │
  │  │  - Users: alice, bob, ...   │    │
  │  │  - Roles: ADMIN, USER       │    │
  │  │  - Clients: api-gateway,    │    │
  │  │             mobile-app      │    │
  │  └─────────────────────────────┘    │
  │                                     │
  │  Issues JWT tokens                  │
  │  Validates credentials              │
  │  Provides login UI                  │
  └─────────────────────────────────────┘
         ↑ login              ↓ JWT token
      [User Browser]       [API Gateway]
                        validates with Keycloak's public key
```


## Summary: The Complete Microservices Ecosystem

```
┌─────────────────────────────────────────────────────────────────────┐
│                 COMPLETE MICROSERVICES ARCHITECTURE                  │
│                                                                     │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────┐                  │
│  │ KEYCLOAK │  │ CONFIG SERVER│  │EUREKA SERVER │                   │
│  │ (Auth)   │  │   (:8888)    │  │  (:8761)     │                   │
│  └──────────┘  └──────────────┘  └──────────────┘                  │
│       ↑               ↑                 ↑                           │
│  Issues JWTs    Serves configs    Registers services                │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                     API GATEWAY (:8080)                      │   │
│  │  Auth ✓  Rate Limiting ✓  Routing ✓  Load Balancing ✓        │   │
│  └──────────────────────────┬───────────────────────────────────┘   │
│                             │                                        │
│         ┌───────────────────┼───────────────────┐                   │
│         │                   │                   │                   │
│  ┌──────▼──────┐   ┌────────▼────┐   ┌──────────▼────┐             │
│  │USER-SERVICE │   │ORDER-SERVICE│   │PAYMENT-SERVICE│             │
│  │  Resilience4j  │   │ Saga Pattern│   │Circuit Breaker│           │
│  │  OpenFeign  │   │ Outbox Ptrn │   │ Kafka Events  │             │
│  └──────┬──────┘   └─────────────┘   └───────────────┘             │
│         │                                                           │
│  ┌──────▼──────────────────────────────────────────────────────┐   │
│  │                  KAFKA / RABBITMQ                            │   │
│  │              (Async Event Streaming)                         │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │        OBSERVABILITY: Prometheus + Loki + Tempo + Grafana    │   │
│  │        Metrics ✓    Logs ✓    Traces ✓    Dashboards ✓       │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Quick Reference Cheat Sheet

```
CONCEPT               WHAT IS IT                        TOOL/LIBRARY
────────────────────────────────────────────────────────────────────────
API Gateway           Single entry point                Spring Cloud Gateway / Kong
Service Discovery     Find service instances            Eureka / K8s DNS
Config Server         Centralized config                Spring Cloud Config
Circuit Breaker       Fail fast on broken deps          Resilience4j
Retry                 Auto-retry transient failures     Resilience4j
Distributed Tracing   Track requests across services    Micrometer + Zipkin/Jaeger
Message Broker        Async communication               Kafka / RabbitMQ
Authentication        Who are you?                      Keycloak / Auth0 + JWT
Authorization         What can you do?                  Gateway + Service-level
Service Mesh          mTLS, observability               Istio / Linkerd
Saga Pattern          Distributed transactions          Choreography / Orchestration
Outbox Pattern        Reliable event publishing         Outbox table + poller
Consumer Group        Parallel Kafka consumers          Kafka Consumer Groups
Dead Letter Queue     Failed messages holding area      RabbitMQ / Kafka DLQ
Health Check          Is service alive?                 /actuator/health
Metrics               System performance numbers        Prometheus + Grafana
Log Aggregation       Centralized logs                  ELK Stack / Loki
Container             Package + run service             Docker
Orchestration         Manage many containers            Kubernetes
```
