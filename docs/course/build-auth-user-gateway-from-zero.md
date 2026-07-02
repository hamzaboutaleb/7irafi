# Build Authentication, User Profiles, and an API Gateway from Zero
## A step-by-step course for Spring Boot beginners

## How to use this course

This course is organized into numbered parts that build on each other. Read each part in order. Before looking at any code, read the explanation that comes before it — the explanation tells you *why* the code is written the way it is.

Every file in this course is shown in full. Never copy a partial block. When a step says "Paste this exactly," it means the whole file, not just the highlighted lines.

If you are new to Java and Spring Boot, Parts 1 and 2 are the most important to read carefully. They establish the mental model you will need for everything that follows.

---

## Part 1 — What We Are Building

### The application

We are building the backend for a platform called **7irafi** (حرفي — Arabic for "craftsman" or "professional"). The platform connects clients with skilled workers such as electricians, plumbers, and painters. Clients can register, browse available workers, and book services. Workers can register under a different role and manage their listings.

The complete system has three backend services and one frontend:

- **auth-service** — handles registration, login, and JWT tokens
- **user-service** — stores and manages user profile data (names, contact information, profile photos)
- **api-gateway** — the single entry point for all HTTP traffic from the frontend; it validates JWTs and routes requests to the right service
- **frontend** — an Angular application that runs in the browser

This course builds the auth-service, the user-service, and the api-gateway from scratch. You will also write the Docker configuration that runs all of them together.

### The architecture

```
Browser (Angular)
        |
        | HTTP on port 4200 (dev server)
        |
   ┌────▼─────────────────────────────────────────┐
   │             api-gateway  :8080               │
   │                                              │
   │  - Validates JWT on every incoming request   │
   │  - Routes /api/auth/** → auth-service        │
   │  - Routes /api/users/** → user-service       │
   │  - Adds X-User-Id header before routing      │
   └──────┬────────────────────────┬──────────────┘
          │ HTTP (internal Docker  │ network)
   ┌──────▼──────────┐    ┌───────▼──────────┐
   │  auth-service   │    │  user-service    │
   │     :8081       │    │     :8082        │
   │                 │    │                  │
   │  database:      │    │  database:       │
   │  auth_db        │    │  user_db         │
   └──────┬──────────┘    └───────┬──────────┘
          │                       │
          └──────────┬────────────┘
                     │
              ┌──────▼──────┐
              │  PostgreSQL │
              │    :5432    │
              └─────────────┘
```

### Why split into services?

A single-service architecture (often called a monolith) puts all logic into one codebase and one database. That works well at the start. However, authentication and user profiles have very different concerns:

- **auth-service** only cares about credentials: email, hashed password, role, and timestamps. It issues and validates JWTs. It should never care about a user's profile photo or phone number.
- **user-service** only cares about profile data: first name, last name, phone, bio, profile image. It should never care about passwords or JWT secrets.

When the concerns are separated, you can scale each service independently, update each service without touching the other, and enforce strict security boundaries (the JWT secret lives *only* inside auth-service and api-gateway — user-service never sees it).

This architecture is called a **microservices** architecture. In this course you are building a small, clear version of it — three services that talk to each other over HTTP.

### The UUID rule

Every user has a single UUID (Universally Unique Identifier) that is created by auth-service during registration. That same UUID is stored in user-service as the user's profile ID. This is the link between the two services.

This is an important design decision. It means:

1. auth-service is the authority on user identity. When a new user registers, auth-service creates the UUID and immediately sends it to user-service via an internal HTTP call to create the profile.
2. The api-gateway reads the UUID from the validated JWT and puts it in an `X-User-Id` HTTP header. Downstream services read that header — they never need to parse a JWT themselves.
3. No service uses an auto-incrementing integer primary key across a service boundary. If two services each have their own auto-increment sequence, user #1 in the auth database and user #1 in the profile database are two completely unrelated records. UUID avoids this collision entirely.

**Why UUID instead of Long id?**

An auto-incrementing Long (1, 2, 3, ...) is predictable. If your API exposes `/users/1`, an attacker can try `/users/2`, `/users/3`, and so on to enumerate all users. A UUID like `f47ac10b-58cc-4372-a567-0e02b2c3d479` is 128 bits of randomness — it cannot be guessed or enumerated. UUIDs are also safe to generate in a distributed system where multiple services or instances might create IDs simultaneously without coordination.

---

## Part 2 — Tools and Vocabulary

Before writing a single line of code, make sure you understand what each tool does. This section defines every term used in the course.

### Java

Java is the programming language we use. All backend services are written in Java 21. Java is statically typed, which means every variable has a declared type that the compiler checks before the program runs. Java code is compiled into bytecode that runs on the Java Virtual Machine (JVM).

### Spring Boot

Spring Boot is a framework that sits on top of the Spring framework. Spring is a large ecosystem for building Java applications — it handles dependency injection, web servers, security, database access, and much more. Spring Boot adds auto-configuration on top: instead of writing hundreds of lines of XML to configure a web server, Spring Boot reads your dependencies and configures sensible defaults automatically. When you add the `spring-boot-starter-web` dependency, Spring Boot automatically starts an embedded Tomcat server.

### Maven

Maven is a build tool for Java projects. It does two things: it manages dependencies (libraries your code needs) and it runs build steps (compile, test, package). All dependency and build configuration lives in a file called `pom.xml` (Project Object Model). When you run `mvn package`, Maven reads `pom.xml`, downloads all declared dependencies from the internet, compiles the code, runs tests, and produces a `.jar` file.

### PostgreSQL

PostgreSQL is the relational database we use to store data. It stores data in tables, where each row is a record and each column is a field. We run it inside Docker so you do not need to install it on your machine.

### Docker

Docker runs software inside isolated containers. A container is a lightweight, self-contained environment that includes the software and everything it needs to run. In this project, each service (PostgreSQL, auth-service, user-service, api-gateway, frontend) runs in its own container. `docker-compose.yml` describes all the containers and how they connect to each other.

### HTTP

HTTP (HyperText Transfer Protocol) is how services talk to each other over a network. A client sends a **request** with a method (GET, POST, PUT, DELETE), a URL, optional headers, and an optional body. The server sends a **response** with a status code (200 OK, 201 Created, 400 Bad Request, 401 Unauthorized, 404 Not Found, 500 Internal Server Error) and an optional body. In this project, the frontend sends HTTP requests to the api-gateway, and the api-gateway sends HTTP requests to auth-service and user-service.

### JWT (JSON Web Token)

A JWT is a self-contained token that proves who a user is. It has three parts separated by dots: a header, a payload, and a signature. The payload contains claims — statements about the user, such as their UUID, email, and role. The signature is created using a secret key. Any party that knows the secret key can verify the signature without asking a database. In this project, auth-service issues JWTs and api-gateway verifies them.

### DTO (Data Transfer Object)

A DTO is a simple object used to carry data between layers or between services over HTTP. It has no business logic — only fields. In this project, DTOs are Java records (explained below). We use DTOs to define exactly what data is allowed into and out of each endpoint. For example, `RegisterRequest` is a DTO that describes the JSON body a client must send when registering.

**Why do we need DTOs instead of using the entity directly?**

The entity (explained below) is the database representation of data. It may contain fields that should never be sent to a client (like `passwordHash`) and fields that a client should never be allowed to set (like `createdAt` or `id`). If you expose your entity directly as the request/response format, you create a security risk and couple your API shape to your database schema. DTOs let you define exactly what goes in and exactly what comes out.

### Entity

An entity is a Java class that maps to a database table. Each instance of the entity class represents one row in the table. Spring Data JPA (a library) reads annotations on the entity class to know the table name, column names, primary key, and constraints.

### Repository

A repository is an interface that gives you database access methods. Instead of writing SQL queries, you declare an interface that extends `JpaRepository`, and Spring Data generates the implementation at startup. You get methods like `save()`, `findById()`, `findAll()`, and `delete()` for free, plus the ability to declare custom query methods by writing method names in a specific pattern.

### Service

A service class contains business logic. It sits between the controller and the repository. The controller receives an HTTP request and calls the service. The service applies the rules (validate the password, hash it, check for duplicates, create the UUID, etc.) and calls the repository to read or write data. The service then returns a result to the controller.

### Controller

A controller class receives HTTP requests, extracts the data from the request, calls the service, and sends back an HTTP response. It is the entry point for every HTTP endpoint. In Spring Boot, a controller is a class annotated with `@RestController`.

---

## Part 3 — Prepare the Project Structure

We will create all directories first before writing any files. This gives you the full picture of what we are building before any code appears.

**Step 1:** Open a terminal and navigate to the root of the repository.

**Step 2:** Create the infrastructure directory for Docker:

```bash
mkdir -p infra/docker/postgres/init
```

**Step 3:** Create the auth-service source tree:

```bash
mkdir -p backend/auth-service/src/main/java/com/herafi/auth_service/entity
mkdir -p backend/auth-service/src/main/java/com/herafi/auth_service/repository
mkdir -p backend/auth-service/src/main/java/com/herafi/auth_service/dto
mkdir -p backend/auth-service/src/main/java/com/herafi/auth_service/service
mkdir -p backend/auth-service/src/main/java/com/herafi/auth_service/controller
mkdir -p backend/auth-service/src/main/java/com/herafi/auth_service/config
mkdir -p backend/auth-service/src/main/java/com/herafi/auth_service/exception
mkdir -p backend/auth-service/src/main/resources
mkdir -p backend/auth-service/src/test/java/com/herafi/auth_service
```

**Step 4:** Create the user-service source tree:

```bash
mkdir -p backend/user-service/src/main/java/com/herafi/user_service/entity
mkdir -p backend/user-service/src/main/java/com/herafi/user_service/repository
mkdir -p backend/user-service/src/main/java/com/herafi/user_service/dto
mkdir -p backend/user-service/src/main/java/com/herafi/user_service/service
mkdir -p backend/user-service/src/main/java/com/herafi/user_service/controller
mkdir -p backend/user-service/src/main/java/com/herafi/user_service/config
mkdir -p backend/user-service/src/main/java/com/herafi/user_service/exception
mkdir -p backend/user-service/src/main/resources
mkdir -p backend/user-service/src/test/java/com/herafi/user_service
```

**Step 5:** Create the api-gateway source tree:

```bash
mkdir -p backend/api-gateway/src/main/java/com/herafi/api_gateway/filter
mkdir -p backend/api-gateway/src/main/java/com/herafi/api_gateway/config
mkdir -p backend/api-gateway/src/main/resources
```

After running these commands, your project layout looks like this:

```
7irafi/
├── infra/
│   └── docker/
│       ├── docker-compose.yml
│       └── postgres/
│           └── init/
│               └── 01-create-databases.sql
└── backend/
    ├── auth-service/
    │   ├── pom.xml
    │   └── src/main/java/com/herafi/auth_service/
    │       ├── AuthServiceApplication.java
    │       ├── entity/
    │       ├── repository/
    │       ├── dto/
    │       ├── service/
    │       ├── controller/
    │       ├── config/
    │       └── exception/
    ├── user-service/
    │   └── (same structure)
    └── api-gateway/
        └── (same structure)
```

The package name `com.herafi.auth_service` uses an underscore rather than a hyphen because Java package names cannot contain hyphens. The artifact name in Maven (`auth-service`) uses the conventional hyphenated form.

---

## Part 4 — Start PostgreSQL with Docker

### Concepts before the code

**Docker Compose** reads a YAML file and starts all declared services in the right order. In our `docker-compose.yml`, the `depends_on` configuration with `condition: service_healthy` tells Docker to wait until one service passes its health check before starting the next. This prevents auth-service from crashing on startup because PostgreSQL is not ready yet.

**Health checks** — Docker can run a command inside a container periodically to test if the service is ready. `pg_isready` is a PostgreSQL utility that checks whether the database server accepts connections. `wget -qO-` fetches a URL and prints the response body; we use it to check the Spring Boot Actuator health endpoint.

**Volumes** — by default, data inside a container disappears when the container is removed. A named volume (`postgres-data`) persists data on the host machine. We also mount the `./postgres/init` directory into the PostgreSQL container so that SQL initialization scripts run automatically on first startup.

**Why two databases?**

One PostgreSQL server runs two databases: `auth_db` and `user_db`. Each service connects to its own database only. This enforces the service boundary: auth-service literally cannot query a user's profile name, and user-service literally cannot query a user's password hash, because they connect to separate databases. If in the future you want to move each database to its own server, you only change the connection URL.

**Environment variables** — hardcoding secrets in source code is dangerous because they end up in git history. We pass all secrets (database passwords, JWT secrets, API keys) as environment variables. In development, they are defined directly in `docker-compose.yml`. In production, they would come from a secrets manager.

### Step 1: Create the file

Create `infra/docker/docker-compose.yml`.

### Step 2: Paste this exactly:

```yaml
services:
  postgres:
    container_name: postgres-dev
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: auth_db
      POSTGRES_USER: herafi
      POSTGRES_PASSWORD: herafi
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./postgres/init:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U herafi -d auth_db"]
      interval: 5s
      timeout: 5s
      retries: 10

  auth-service:
    container_name: auth-service-dev
    build:
      context: ../../backend/auth-service
      dockerfile: Dockerfile
    environment:
      SERVER_PORT: 8081
      DB_URL: jdbc:postgresql://postgres:5432/auth_db
      DB_USERNAME: herafi
      DB_PASSWORD: herafi
      SHOW_SQL: "true"
      USER_SERVICE_URL: http://user-service:8082
      INTERNAL_API_KEY: 7irafi-development-internal-key
      JWT_SECRET: 7irafi-development-jwt-secret-change-me-2026
      JWT_ISSUER: 7irafi-auth-service
    ports:
      - "8081:8081"
    depends_on:
      postgres:
        condition: service_healthy
      user-service:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8081/actuator/health | grep -q UP"]
      interval: 5s
      timeout: 5s
      retries: 20

  user-service:
    container_name: user-service-dev
    build:
      context: ../../backend/user-service
      dockerfile: Dockerfile
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/user_db
      DB_USERNAME: herafi
      DB_PASSWORD: herafi
      SHOW_SQL: "true"
      GATEWAY_SHARED_SECRET: 7irafi-development-gateway-key
      INTERNAL_API_KEY: 7irafi-development-internal-key
    ports:
      - "8082:8082"
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8082/actuator/health | grep -q UP"]
      interval: 5s
      timeout: 5s
      retries: 20

  api-gateway:
    container_name: api-gateway-dev
    build:
      context: ../../backend/api-gateway
      dockerfile: Dockerfile
    environment:
      SERVER_PORT: 8080
      AUTH_SERVICE_URL: http://auth-service:8081
      USER_SERVICE_URL: http://user-service:8082
      JWT_SECRET: 7irafi-development-jwt-secret-change-me-2026
      JWT_ISSUER: 7irafi-auth-service
      GATEWAY_SHARED_SECRET: 7irafi-development-gateway-key
      FRONTEND_ORIGIN: http://localhost:4200
    ports:
      - "8080:8080"
    depends_on:
      auth-service:
        condition: service_healthy
      user-service:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health | grep -q UP"]
      interval: 5s
      timeout: 5s
      retries: 20

  frontend:
    container_name: frontend-dev
    image: frontend-dev:latest
    build:
      context: ../..
      dockerfile: infra/docker/frontend/dockerfiles/angular.dev.Dockerfile
    ports:
      - "4200:4200"
    volumes:
      - ../../frontend:/app
      - /app/node_modules

volumes:
  postgres-data:
```

**Line-by-line notes on what is not obvious:**

- `image: postgres:17-alpine` — Alpine is a minimal Linux distribution; the image is smaller than the default Debian-based PostgreSQL image.
- `./postgres/init:/docker-entrypoint-initdb.d:ro` — PostgreSQL automatically executes every `.sql` and `.sh` file it finds in `/docker-entrypoint-initdb.d` on the very first run (when the data volume is empty). `:ro` mounts the directory as read-only inside the container.
- `DB_URL: jdbc:postgresql://postgres:5432/auth_db` — inside the Docker network, services refer to each other by their service name. `postgres` here is the service name defined at the top of this file, not `localhost`.
- `INTERNAL_API_KEY` — when auth-service calls user-service to create a profile, it sends this key in a header. user-service checks the header so that only auth-service (not a random HTTP client) can call that internal endpoint.
- `GATEWAY_SHARED_SECRET` — the api-gateway puts this in a header when forwarding requests to downstream services. The downstream services verify the header to ensure they only accept traffic from the gateway.
- `JWT_SECRET` must be the same value in both `auth-service` and `api-gateway`. auth-service signs tokens with it; api-gateway verifies tokens with it.

### Step 3: Create the database initialization file

Create `infra/docker/postgres/init/01-create-databases.sql`.

### Step 4: Paste this exactly:

```sql
SELECT 'CREATE DATABASE auth_db'
WHERE NOT EXISTS (
    SELECT FROM pg_database WHERE datname = 'auth_db'
)\gexec

SELECT 'CREATE DATABASE user_db'
WHERE NOT EXISTS (
    SELECT FROM pg_database WHERE datname = 'user_db'
)\gexec
```

**What this does:** PostgreSQL's `POSTGRES_DB` environment variable creates a single default database (`auth_db`). We also need `user_db`. This script creates `user_db` if it does not already exist. The `\gexec` command is a `psql` meta-command that takes the result of the SELECT as a SQL string and executes it. Using `WHERE NOT EXISTS` makes the script safe to run multiple times without error.

The filename prefix `01-` controls execution order. PostgreSQL sorts init scripts alphabetically, so if you add more scripts later, `02-...sql` runs after `01-...sql`.

### Step 5: Start the database

```bash
cd infra/docker
docker compose up postgres -d
```

The `-d` flag runs the container in detached mode (in the background). You can check that it started correctly with:

```bash
docker compose logs postgres
```

Look for the line `database system is ready to accept connections`. Once you see it, PostgreSQL is running.

---

## Part 5 — auth-service pom.xml and Main Application

### Understanding pom.xml

A `pom.xml` has several key sections:

- **`<parent>`** — We inherit from `spring-boot-starter-parent`. This parent POM manages the versions of all Spring Boot dependencies so you do not have to specify a version number for each one.
- **`<groupId>`** — A reverse-domain-name identifier for your organization: `com.herafi`.
- **`<artifactId>`** — The name of this specific project: `auth-service`.
- **`<version>`** — `0.0.1-SNAPSHOT`. SNAPSHOT means this is a development version, not a release.
- **`<properties>`** — Sets Java 21 as the compiler target.
- **`<dependencies>`** — The libraries this service needs.
- **`<build><plugins>`** — Build tools configuration.

**What each dependency does:**

| Dependency | What it adds |
|---|---|
| `spring-boot-starter-data-jpa` | JPA (Java Persistence API), Hibernate ORM, Spring Data repositories |
| `spring-boot-starter-security` | Spring Security — filters, authentication, authorization |
| `spring-boot-starter-validation` | Bean Validation (Jakarta Validation) — `@NotBlank`, `@Email`, `@Size` |
| `spring-boot-starter-webmvc` | Embedded Tomcat, Spring MVC, `@RestController` |
| `spring-boot-starter-actuator` | `/actuator/health` and other operational endpoints |
| `spring-security-oauth2-jose` | JWT encoding and decoding (JOSE = JSON Object Signing and Encryption) |
| `spring-boot-devtools` | Automatic restart on code changes during development |
| `postgresql` | JDBC driver that lets Java talk to PostgreSQL |
| `lombok` | Annotation processor that generates boilerplate Java code at compile time |
| `h2` (test scope) | In-memory database used during tests so tests do not need a running PostgreSQL |

**Lombok and the annotation processor:** Lombok works at compile time, not runtime. The `maven-compiler-plugin` block in the build section tells the Java compiler to run the Lombok annotation processor. If this block is missing, Lombok annotations are silently ignored and your code will not compile. The `<excludes>` block in `spring-boot-maven-plugin` makes sure Lombok is not bundled inside the final `.jar` — it is only needed during compilation.

### Step 1: Create the file

Create `backend/auth-service/pom.xml`.

### Step 2: Paste this exactly:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>4.1.0</version>
		<relativePath/> <!-- lookup parent from repository -->
	</parent>
	<groupId>com.herafi</groupId>
	<artifactId>auth-service</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<name/>
	<description/>
	<url/>
	<licenses>
		<license/>
	</licenses>
	<developers>
		<developer/>
	</developers>
	<scm>
		<connection/>
		<developerConnection/>
		<tag/>
		<url/>
	</scm>
	<properties>
		<java.version>21</java.version>
	</properties>
	<dependencies>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-actuator</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.security</groupId>
			<artifactId>spring-security-oauth2-jose</artifactId>
		</dependency>

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-devtools</artifactId>
			<scope>runtime</scope>
			<optional>true</optional>
		</dependency>
		<dependency>
			<groupId>org.postgresql</groupId>
			<artifactId>postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>org.projectlombok</groupId>
			<artifactId>lombok</artifactId>
			<optional>true</optional>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>com.h2database</groupId>
			<artifactId>h2</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>

	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
				<configuration>
					<excludes>
						<exclude>
							<groupId>org.projectlombok</groupId>
							<artifactId>lombok</artifactId>
						</exclude>
					</excludes>
				</configuration>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-compiler-plugin</artifactId>
				<executions>
					<execution>
						<id>default-compile</id>
						<phase>compile</phase>
						<goals>
							<goal>compile</goal>
						</goals>
						<configuration>
							<annotationProcessorPaths>
								<path>
									<groupId>org.projectlombok</groupId>
									<artifactId>lombok</artifactId>
								</path>
							</annotationProcessorPaths>
						</configuration>
					</execution>
					<execution>
						<id>default-testCompile</id>
						<phase>test-compile</phase>
						<goals>
							<goal>testCompile</goal>
						</goals>
						<configuration>
							<annotationProcessorPaths>
								<path>
									<groupId>org.projectlombok</groupId>
									<artifactId>lombok</artifactId>
								</path>
							</annotationProcessorPaths>
						</configuration>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>

</project>
```

### What is @SpringBootApplication?

`@SpringBootApplication` is a convenience annotation that combines three annotations:

- `@SpringBootConfiguration` — marks this class as a configuration source (similar to `@Configuration`).
- `@EnableAutoConfiguration` — tells Spring Boot to automatically configure beans based on what is on the classpath. If it sees the PostgreSQL driver and JPA dependencies, it automatically creates a `DataSource` and an `EntityManagerFactory`.
- `@ComponentScan` — tells Spring to scan the current package and all sub-packages for classes annotated with `@Component`, `@Service`, `@Repository`, `@Controller`, `@RestController`, etc., and register them as Spring beans.

`SpringApplication.run(AuthServiceApplication.class, args)` starts the embedded Tomcat server, runs all auto-configuration, creates all beans found by component scanning, and begins accepting HTTP requests.

### Step 3: Create the file

Create `backend/auth-service/src/main/java/com/herafi/auth_service/AuthServiceApplication.java`.

### Step 4: Paste this exactly:

```java
package com.herafi.auth_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}

}
```

This is the entry point of the application. Every Spring Boot application has exactly one class like this. Notice how small it is — Spring Boot's auto-configuration does the heavy lifting.

---

## Part 6 — The UserCredential Entity

### Concepts before the code

An **entity** is Java's representation of a database row. When you call `repository.save(credential)`, JPA (specifically Hibernate, which is JPA's most common implementation) translates the entity object into an SQL `INSERT` or `UPDATE` statement. When you call `repository.findById(id)`, Hibernate translates the result of a SQL `SELECT` back into an entity object.

**`@Entity`** tells Hibernate that this class maps to a database table. Without this annotation, Hibernate ignores the class entirely.

**`@Table(name = "users")`** specifies the exact table name. By default, Hibernate would use the class name (`UserCredential`), which would create a table called `user_credential`. We override it to `users` because that is a cleaner name for the table that stores user records. The `@Table` annotation is optional if you are happy with the default name.

**`@Id`** marks the primary key field. Every entity must have exactly one field annotated with `@Id`. In many tutorials you will see `@Id` combined with `@GeneratedValue(strategy = GenerationType.IDENTITY)`, which delegates ID generation to the database (auto-increment). We do **not** use `@GeneratedValue` here. Instead, the service layer generates the UUID before saving. This is a deliberate choice: the UUID must be created in application code so it can be sent to user-service in the same registration transaction before the row is committed. If we relied on the database to generate it, we would not know the ID until after the INSERT completes.

**`@Column`** lets you specify constraints on the database column:

- `nullable = false` — the column gets a `NOT NULL` constraint. JPA will also validate this before trying to INSERT.
- `unique = true` — adds a UNIQUE constraint to the column. This prevents two rows from having the same email address.
- `length = 255` — sets the `VARCHAR` length. For a `String` field, Hibernate defaults to `VARCHAR(255)` anyway, but being explicit makes your intent clear.
- `updatable = false` — when Hibernate generates an UPDATE statement, it omits this column. We use this on `createdAt` so that it can never accidentally be changed after the row is first created.
- `name = "password"` — the Java field is named `passwordHash` (to make the code readable), but the column in the database is named `password`. The `name` attribute maps one to the other.

**`@Enumerated(EnumType.STRING)`** tells Hibernate how to store an enum value in the database. There are two options:

- `EnumType.ORDINAL` (the default, and the dangerous one) — stores the ordinal position of the enum constant: `USER` becomes `0`, `ADMIN` becomes `1`. If you ever add a new constant between existing ones (e.g., `MODERATOR` between `USER` and `ADMIN`), the ordinal values of all subsequent constants shift, corrupting all existing data in the database.
- `EnumType.STRING` — stores the name of the enum constant as a string: `"USER"`, `"ADMIN"`. Adding new constants anywhere never affects existing data. Always use `EnumType.STRING`.

**`@PrePersist` and `@PreUpdate`** are JPA lifecycle callbacks. JPA calls the method annotated with `@PrePersist` immediately before it executes an INSERT statement. It calls the method annotated with `@PreUpdate` immediately before it executes an UPDATE statement. We use these to set the `createdAt` and `updatedAt` timestamps automatically. The application code never has to remember to set them.

**Why do we store `passwordHash` not `password`?**

Storing plaintext passwords is a critical security vulnerability. If the database is ever compromised, every user's password is exposed. We store a one-way cryptographic hash of the password (created by bcrypt, which we configure later). When a user logs in, we hash the submitted password and compare it to the stored hash. The original password is never stored anywhere. The field is named `passwordHash` to make this fact self-documenting in the code. The column in the database is named `password` because from the database's perspective, it is just storing what it receives.

**Lombok annotations:**

- **`@Getter`** — generates a `getXxx()` method for every field. For example, `getEmail()`, `getPasswordHash()`, `getRole()`.
- **`@Setter`** — generates a `setXxx(value)` method for every field. For example, `setEmail(String email)`.
- **`@NoArgsConstructor`** — generates a constructor with no parameters: `new UserCredential()`. JPA requires a no-argument constructor on every entity. Without it, Hibernate cannot instantiate the object when reading rows from the database.

Without Lombok, you would write all these methods by hand — dozens of lines of repetitive boilerplate. Lombok generates them at compile time so the `.class` file contains them, but your `.java` source file stays clean.

### Step 1: Create the file

Create `backend/auth-service/src/main/java/com/herafi/auth_service/entity/UserCredential.java`.

### Step 2: Paste this exactly:

```java
package com.herafi.auth_service.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class UserCredential {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
```

**Reading this class top to bottom:**

1. `@Getter @Setter @NoArgsConstructor` — Lombok generates getters, setters, and the no-arg constructor.
2. `@Entity @Table(name = "users")` — this class maps to the `users` table.
3. `UUID id` with `@Id` — the primary key, stored as a UUID. No auto-generation — the service layer sets this before saving.
4. `String email` — must be present (`nullable = false`), must be unique (`unique = true`), max 255 characters.
5. `String passwordHash` — the Java field is named `passwordHash`, but the database column is named `password`. Must be present.
6. `Role role` — stored as the string `"USER"` or `"ADMIN"`. Defaults to `Role.USER` when the object is constructed.
7. `Instant createdAt` — set once by `@PrePersist`, never updated (because `updatable = false`).
8. `Instant updatedAt` — set by `@PrePersist` on creation, then updated by `@PreUpdate` on every save.

`Instant` represents a moment in time on the UTC timeline. It is the correct type for timestamps because it has no timezone ambiguity.

---

## Part 7 — The Role Enum

### Concepts before the code

A Java **enum** (enumeration) is a type that can only hold one of a fixed set of named constants. Using `String role` in your entity would allow any string value — including typos like `"Adimn"` or invalid values like `"superuser"`. An enum restricts the possible values to exactly the constants you define, and the compiler enforces this at every assignment site.

**Why do we need a Role enum instead of a String?**

Three reasons:

1. **Type safety** — you cannot accidentally assign `"ADMN"` to a `Role` field. The compiler rejects it.
2. **Refactoring safety** — if you rename a role, your IDE finds every usage. With a String, you would have to search for the literal text `"ADMIN"` everywhere.
3. **Switch expressions** — when you write business logic like "if the user is an ADMIN, allow access," an enum enables exhaustive switch expressions. The compiler warns you if you forget to handle a case.

### Step 1: Create the file

Create `backend/auth-service/src/main/java/com/herafi/auth_service/entity/Role.java`.

### Step 2: Paste this exactly:

```java
package com.herafi.auth_service.entity;

public enum Role {
    USER,
    ADMIN
}
```

`USER` is the default role assigned to every new registration. `ADMIN` is reserved for administrative accounts. The constants are written in uppercase by Java convention for enum constants.

When this enum is stored in the database (via `@Enumerated(EnumType.STRING)` on the entity), PostgreSQL stores the strings `"USER"` and `"ADMIN"`.

---

## Part 8 — The UserCredentialRepository

### Concepts before the code

**JpaRepository** is a Spring Data interface. When you declare your own interface that extends it, Spring Data generates a complete implementation at startup — you write zero implementation code. The interface is parameterized with two types: the entity type (`UserCredential`) and the primary key type (`UUID`).

Out of the box, every `JpaRepository` gives you:

| Method | What it does |
|---|---|
| `save(entity)` | INSERT if new, UPDATE if already saved |
| `findById(id)` | SELECT by primary key, returns `Optional<T>` |
| `findAll()` | SELECT all rows |
| `deleteById(id)` | DELETE by primary key |
| `existsById(id)` | returns true if a row with that ID exists |
| `count()` | returns the total number of rows |

`Optional<T>` is a Java wrapper that forces the caller to handle the case where no result was found. Instead of returning `null` (which leads to NullPointerExceptions), `findById` returns `Optional.empty()` when no row matches. The caller then decides what to do: throw an exception, return a default, or map the value.

**How Spring Data generates queries from method names:**

Spring Data reads the method name and parses it into a query. The rules are:

- Start with `findBy`, `existsBy`, `countBy`, or `deleteBy`.
- Follow with field name(s) from the entity.
- Combine with `And`, `Or`, `OrderBy`, etc.

For `findByEmail(String email)`:
1. Spring Data sees `findBy` — it knows this is a SELECT query.
2. It sees `Email` — it looks for a field named `email` on `UserCredential`.
3. It generates: `SELECT u FROM UserCredential u WHERE u.email = :email`.
4. The `String email` parameter is bound to `:email`.

For `existsByEmail(String email)`:
1. `existsBy` — generates a `SELECT COUNT(*)` query (or an existence check, depending on the JPA provider).
2. Returns `true` if at least one row matches, `false` otherwise.

This convention is called **derived query methods**. It covers the vast majority of simple queries without writing any SQL or JPQL.

### Step 1: Create the file

Create `backend/auth-service/src/main/java/com/herafi/auth_service/repository/UserCredentialRepository.java`.

### Step 2: Paste this exactly:

```java
package com.herafi.auth_service.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.herafi.auth_service.entity.UserCredential;

public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {

    Optional<UserCredential> findByEmail(String email);

    boolean existsByEmail(String email);
}
```

This interface needs only three lines of actual code (the two method declarations) because `JpaRepository<UserCredential, UUID>` already provides every standard CRUD operation.

`findByEmail` is used during login: look up the credential record by email, then verify the submitted password against the stored hash.

`existsByEmail` is used during registration: check if an account with this email already exists before creating a new one. Using `existsByEmail` instead of `findByEmail` is more efficient — it only checks for existence and does not load the full entity.

---

## Part 9 — DTOs for auth-service

### Concepts before the code

**Java records** (introduced in Java 16) are a concise way to declare immutable data holders. A record declaration like:

```java
public record RegisterRequest(String email, String password) {}
```

automatically generates:
- A constructor that accepts all fields.
- A getter method for each field (named `email()` and `password()`, not `getEmail()` — records use the field name directly as the method name).
- `equals()`, `hashCode()`, and `toString()` implementations based on all fields.
- The fields are `final` — once the record is constructed, its values cannot be changed.

This makes records ideal for DTOs, which should be immutable data containers. Compared to a regular class with Lombok, a record is even more concise and communicates immutability as part of the language, not just a convention.

**Bean Validation annotations:**

When a controller method parameter is annotated with `@Valid` (covered in a later part), Spring calls the Bean Validation framework on the incoming object before passing it to your method. The validation annotations declare constraints that each field must satisfy:

- **`@NotBlank`** — the field must not be null, and must contain at least one non-whitespace character. It rejects `null`, `""`, and `"   "`. Use this for string fields that are required.
- **`@Email`** — the field must be a syntactically valid email address. It checks for an `@` symbol and a domain. It does not verify that the address exists, only that it looks like an email.
- **`@Size(min = 8, max = 128)`** — the string's length must be between 8 and 128 characters inclusive. For passwords, `min = 8` enforces a minimum length. For email, `max = 255` matches the database column constraint so validation rejects oversized input before it even reaches the database.

If validation fails, Spring automatically returns a `400 Bad Request` response. We will configure the exact error response shape in the exception handler (covered in a later part). The DTOs define the constraints; the exception handler shapes the error response.

**Why do we need DTOs instead of using the entity directly?** (Expanded for this context)

Consider `RegisterRequest`: it has `firstName` and `lastName` because registration needs that data (to create the user profile). The `UserCredential` entity does not have those fields — auth-service does not store profile names. The DTO is the shape of the HTTP request. The entity is the shape of the database row. They serve different purposes.

Consider `AuthResponse`: it includes the JWT token, a token type, an expiry duration, and a summary of the user. None of these fields exist on the entity. The DTO is constructed by the service layer from multiple sources.

---

### RegisterRequest

**Purpose:** The JSON body that a client sends when creating a new account. It contains login credentials and the minimum profile information needed to create a user profile in user-service.

### Step 1: Create the file

Create `backend/auth-service/src/main/java/com/herafi/auth_service/dto/RegisterRequest.java`.

### Step 2: Paste this exactly:

```java
package com.herafi.auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 8, max = 128)
        String password,

        @NotBlank
        @Size(max = 50)
        String firstName,

        @NotBlank
        @Size(max = 50)
        String lastName
) {
}
```

**Annotation notes:**

- `email` has three constraints: not blank, must look like an email, max 255 characters (matching the database column length).
- `password` has `min = 8` to reject weak passwords at the API boundary. It has `max = 128` to prevent unreasonably long inputs. Note: `@NotBlank` also applies, so an empty password string is rejected before the size check even runs.
- `firstName` and `lastName` have no minimum length beyond what `@NotBlank` enforces. A one-character first name is valid. The max of 50 matches a reasonable column length we will set in user-service.

After registration, `firstName` and `lastName` are immediately forwarded to user-service to create the user's profile. Auth-service does not store them.

---

### LoginRequest

**Purpose:** The JSON body that a client sends to authenticate.

### Step 1: Create the file

Create `backend/auth-service/src/main/java/com/herafi/auth_service/dto/LoginRequest.java`.

### Step 2: Paste this exactly:

```java
package com.herafi.auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(max = 128)
        String password
) {
}
```

Compared to `RegisterRequest`, `LoginRequest` is simpler — only `email` and `password`. There is no `firstName` or `lastName` because login only needs credentials. The `@Size` constraints match `RegisterRequest` for consistency: if a value is too long to have been registered, it cannot match any stored account, and it is better to reject it at validation than to hit the database.

Note that `password` does not have `@Size(min = 8)` in `LoginRequest`. This is deliberate. If a password is shorter than 8 characters, it will simply not match any stored hash. Adding a minimum length to the login DTO would let an attacker know that an account definitely does not use a short password. It is safer to let the authentication logic reject it uniformly.

---

### AuthResponse

**Purpose:** The JSON body that auth-service returns after a successful login or registration. It contains the JWT token and a brief user summary.

### Step 1: Create the file

Create `backend/auth-service/src/main/java/com/herafi/auth_service/dto/AuthResponse.java`.

### Step 2: Paste this exactly:

```java
package com.herafi.auth_service.dto;

import java.util.UUID;

import com.herafi.auth_service.entity.Role;
import com.herafi.auth_service.entity.UserCredential;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserSummary user
) {

    public static AuthResponse from(
            UserCredential credential,
            String accessToken,
            long expiresIn) {
        return new AuthResponse(
                accessToken,
                "Bearer",
                expiresIn,
                new UserSummary(
                        credential.getId(),
                        credential.getEmail(),
                        credential.getRole()
                )
        );
    }

    public record UserSummary(
            UUID id,
            String email,
            Role role
    ) {
    }
}
```

**Design notes:**

- `tokenType` is always `"Bearer"`. This is the OAuth 2.0 convention for JWT access tokens. The client is expected to include this token in the `Authorization` header as `Authorization: Bearer <token>`.
- `expiresIn` is in seconds. The client uses this to know when to request a new token.
- `UserSummary` is a **nested record**. Records can be nested inside other records. `UserSummary` contains only the information the frontend needs to display immediately after login: the user's ID, email, and role. It does not include the password hash or timestamps.
- The static `from()` factory method is a convenience: given a `UserCredential` entity, a token string, and an expiry duration, it builds the full `AuthResponse`. This pattern keeps the construction logic in one place. The service layer calls `AuthResponse.from(credential, token, expiresIn)` instead of constructing it manually every time.

The `UserSummary` contains the UUID (`id`). The frontend stores this locally and can include it in requests, but the API gateway always re-extracts the ID from the validated JWT — it never trusts a user-supplied ID.

---

### CreateUserProfileRequest

**Purpose:** The JSON body that auth-service sends to user-service immediately after creating a new account. This is an internal service-to-service DTO, not a client-facing one.

### Step 1: Create the file

Create `backend/auth-service/src/main/java/com/herafi/auth_service/dto/CreateUserProfileRequest.java`.

### Step 2: Paste this exactly:

```java
package com.herafi.auth_service.dto;

import java.util.UUID;

public record CreateUserProfileRequest(
        UUID id,
        String email,
        String firstName,
        String lastName
) {
}
```

**Design notes:**

- This record has no validation annotations. Validation annotations on a DTO make sense when the data comes from an untrusted external source (a client HTTP request). Here, auth-service constructs this object itself from already-validated registration data, so there is nothing to validate.
- The `id` field carries the UUID that auth-service generated for this user. User-service will use this UUID as the primary key of the new profile row. This is the link between the two databases.
- Auth-service sends this to user-service using a `RestClient` (or `WebClient`) HTTP call with the `INTERNAL_API_KEY` header. User-service verifies the key and creates the profile.

---

### ApiErrorResponse

**Purpose:** A standardized error response body that all error responses from auth-service use. Having a consistent error shape means the frontend always knows what to expect when something goes wrong.

### Step 1: Create the file

Create `backend/auth-service/src/main/java/com/herafi/auth_service/dto/ApiErrorResponse.java`.

### Step 2: Paste this exactly:

```java
package com.herafi.auth_service.dto;

import java.util.List;

public record ApiErrorResponse(
        boolean success,
        ErrorDetails error
) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(false, new ErrorDetails(code, message, List.of()));
    }

    public static ApiErrorResponse validation(List<FieldValidationError> errors) {
        return new ApiErrorResponse(
                false,
                new ErrorDetails("VALIDATION_ERROR", "Validation failed", errors)
        );
    }

    public record ErrorDetails(
            String code,
            String message,
            List<FieldValidationError> errors
    ) {
    }

    public record FieldValidationError(
            String field,
            String message
    ) {
    }
}
```

**Design notes:**

- `success` is always `false` in an error response. Including it means the frontend can check `response.success` on every response without checking the HTTP status code first.
- `ErrorDetails.code` is a machine-readable string like `"VALIDATION_ERROR"`, `"USER_ALREADY_EXISTS"`, or `"INVALID_CREDENTIALS"`. The frontend can switch on this string to show the right message in the right language.
- `ErrorDetails.message` is a human-readable English description. Useful for debugging.
- `ErrorDetails.errors` is a list of `FieldValidationError` objects. This list is populated only for validation errors; for all other error types it is an empty list (`List.of()`). Each `FieldValidationError` names the specific field that failed (`"email"`, `"password"`) and gives a message describing the violation.

**Example JSON for a validation error:**

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "errors": [
      { "field": "email", "message": "must be a well-formed email address" },
      { "field": "password", "message": "size must be between 8 and 128" }
    ]
  }
}
```

**Example JSON for a business logic error:**

```json
{
  "success": false,
  "error": {
    "code": "USER_ALREADY_EXISTS",
    "message": "An account with this email address already exists",
    "errors": []
  }
}
```

The two static factory methods make construction readable:

- `ApiErrorResponse.of("USER_ALREADY_EXISTS", "An account with this email address already exists")` — for single-message errors.
- `ApiErrorResponse.validation(fieldErrors)` — for validation errors, which always use the `"VALIDATION_ERROR"` code and always have a list of per-field errors.

This error structure is the contract between auth-service and the frontend. Every time something goes wrong, the frontend receives this shape. The exception handler (covered in a later part) translates Java exceptions into `ApiErrorResponse` objects and returns them with the appropriate HTTP status code.
# Part 10: Hash passwords with BCrypt

## What is `@Configuration`?

When you mark a class with `@Configuration`, you are telling Spring that this class is a **factory for beans**. A bean is simply a Java object that Spring creates, manages, and makes available for injection throughout your application. Spring reads `@Configuration` classes at startup, calls every method annotated with `@Bean`, and stores the returned objects in a container called the **ApplicationContext**.

You never call `new BCryptPasswordEncoder()` yourself in your services. Instead you declare one bean here, and Spring hands that single shared instance to every class that needs it. This is the core idea behind **Inversion of Control (IoC)**: the framework is in control of object creation and wiring, not your code.

## What is `@Bean`?

`@Bean` is a method-level annotation that you place inside a `@Configuration` class. It tells Spring: "call this method once at startup, take whatever it returns, and register it in the container under the return type." Any other Spring-managed component that declares the same type as a constructor parameter will receive this object automatically — this is called **dependency injection**.

## What does `BCryptPasswordEncoder` do?

BCrypt is a one-way hashing function designed specifically for passwords. When a user registers with the password `"mysecret"`, BCrypt runs it through a slow, computationally expensive algorithm and produces a string that looks like:

```
$2a$10$7EqJtq98hPqEX7fNZaFWoO29mdUGYEkb3M0QOXN1TjL9HY8N.5H.C
```

This string **cannot be reversed**. You can never get `"mysecret"` back from it. When a user tries to log in later, BCrypt takes their submitted password, applies the same process, and compares the output — it never compares raw strings. This is why `BCryptPasswordEncoder` has two separate methods: `encode(rawPassword)` for registration and `matches(rawPassword, encodedPassword)` for login verification.

The `10` in the hash above is the **cost factor**. BCrypt intentionally takes time, making brute-force attacks impractical even if someone steals your database.

---

## Step 1: Create file `PasswordConfig.java`

Path: `backend/auth-service/src/main/java/com/herafi/auth_service/config/PasswordConfig.java`

## Step 2: Paste this exactly:

```java
package com.herafi.auth_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

This file does exactly one thing: it registers a `PasswordEncoder` bean. Every other class in the application that needs to hash or verify passwords will receive this same `BCryptPasswordEncoder` instance through constructor injection.

---

# Part 11: Configure JWT encoding with `JwtConfig`

## What is a JWT?

A JSON Web Token (JWT) is a compact, self-contained string that proves who you are. After a successful login, auth-service creates one and sends it back to the client. The client stores it and attaches it to every subsequent request. Other services can read it without calling auth-service again, because the token is **cryptographically signed** — tampering with it makes the signature invalid.

## What is `JwtEncoder`?

`JwtEncoder` is an interface from Spring Security's OAuth2 support. It takes a set of claims (key-value pairs like `sub`, `email`, `role`) plus a header (which algorithm to use) and produces a signed JWT string. The concrete implementation we use is `NimbusJwtEncoder`, built on top of the Nimbus JOSE library — the industry-standard Java library for JWT operations.

You never import `NimbusJwtEncoder` directly in your services; you depend on the `JwtEncoder` interface. If you ever swap the underlying library, your service code changes nothing.

## What is `MacAlgorithm.HS256`?

HS256 stands for **HMAC-SHA256**:

- **HMAC** — Hash-based Message Authentication Code. It is a construction that takes your data plus a secret key and produces a fixed-length authentication tag.
- **SHA-256** — the underlying hash function (256-bit output).
- **Symmetric** — the same secret key is used both to sign the token and to verify it. This is simpler than asymmetric (RSA/EC) algorithms and fast enough for a service that owns all the verification.

The secret key must be at least 256 bits (32 bytes) for HS256. The code enforces this at startup with a hard check.

## What is `@Value`?

`@Value("${property.name}")` tells Spring to read a value from `application.properties` (or environment variables) and inject it into this parameter. This keeps secrets out of source code.

---

## Step 1: Create file `JwtConfig.java`

Path: `backend/auth-service/src/main/java/com/herafi/auth_service/config/JwtConfig.java`

## Step 2: Paste this exactly:

```java
package com.herafi.auth_service.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfig {

    @Bean
    SecretKey jwtSecretKey(@Value("${security.jwt.secret}") String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT secret must contain at least 32 bytes");
        }

        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey secretKey) {
        return NimbusJwtEncoder
                .withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }
}
```

Notice that `jwtEncoder` takes `SecretKey secretKey` as a parameter. Spring sees that it needs a `SecretKey` bean, finds the one declared by `jwtSecretKey()` above, and injects it automatically. The two `@Bean` methods are wired together purely through types, with no string names required.

---

# Part 12: Configure the HTTP client with `UserServiceClientConfig`

## What is `RestClient`?

`RestClient` is Spring Framework 6's modern, fluent HTTP client. It replaces the older `RestTemplate`. Rather than writing low-level HTTP code (`HttpURLConnection`, `OkHttpClient`, etc.), you declare a base URL once and then write calls like:

```java
restClient.post().uri("/internal/users").body(request).retrieve().toBodilessEntity();
```

This is readable, testable, and handles serialization to/from JSON automatically. `RestClient` is synchronous and blocking — appropriate for the request-scoped calls auth-service makes to user-service during registration.

## Why configure it as a bean?

Registering `RestClient` as a bean in `@Configuration` means:

1. The base URL is read from config once at startup.
2. Any class that needs to call user-service gets the same pre-configured client injected — no one hard-codes URLs.
3. In tests you can inject a mock or point it at a test server.

---

## Step 1: Create file `UserServiceClientConfig.java`

Path: `backend/auth-service/src/main/java/com/herafi/auth_service/config/UserServiceClientConfig.java`

## Step 2: Paste this exactly:

```java
package com.herafi.auth_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class UserServiceClientConfig { 

    @Bean
    RestClient userServiceRestClient(
            @Value("${services.user-service.url}") String userServiceUrl) {
        return RestClient.builder()
                .baseUrl(userServiceUrl)
                .build();
    }
}
```

The bean is named `userServiceRestClient`. When `UserProfileClient` declares a constructor parameter of type `RestClient`, Spring uses this name to resolve which `RestClient` bean to inject — important if you ever have more than one.

---

# Part 13: Lock down the service with `SecurityConfig`

## What is the Spring Security filter chain?

Every HTTP request that hits a Spring Boot application passes through a series of **filters** before it reaches your controller. Spring Security inserts its own chain of filters into this pipeline. These filters handle things like: reading the `Authorization` header, validating tokens, rejecting unauthenticated requests, and more.

`SecurityFilterChain` is the object that configures this chain. You declare it as a `@Bean` and use a fluent DSL (`HttpSecurity`) to set the rules.

## What is `SessionCreationPolicy.STATELESS`?

By default, Spring Security creates an HTTP session for each user and stores the authentication object in it (using cookies). This is the traditional pattern for server-rendered web apps.

For a REST API used by mobile apps, SPAs, or other services, sessions are the wrong tool:

- Sessions require server-side storage and sticky routing in a cluster.
- Mobile clients and cross-origin frontends cannot rely on cookies reliably.
- We issue JWTs instead — the client carries the proof of identity in every request header.

Setting `SessionCreationPolicy.STATELESS` tells Spring Security: **never create a session, never read from a session**. Every request is authenticated from scratch using the JWT in the `Authorization` header.

## Why disable CSRF?

CSRF (Cross-Site Request Forgery) attacks exploit the fact that browsers automatically attach cookies to same-origin requests. If authentication relies on a session cookie, a malicious website can trick a logged-in user's browser into making unwanted requests.

When you are stateless and use bearer tokens (JWT in the `Authorization` header), the browser does not automatically attach the token to cross-origin requests. CSRF protection is therefore unnecessary and only adds complexity. `csrf().disable()` is safe for stateless token-based APIs.

## What is `DaoAuthenticationProvider`?

`DaoAuthenticationProvider` is a Spring Security class that connects two things:

1. A `UserDetailsService` — to load the user record by username.
2. A `PasswordEncoder` — to compare the submitted password against the stored hash.

It is called "Dao" (Data Access Object) because it goes to a data store to look up the user. You hand it your `CustomUserDetailsService` and `BCryptPasswordEncoder`, and it handles the full verification logic automatically. You do not write any `if (bcrypt.matches(...))` logic yourself.

## What is `AuthenticationManager`?

`AuthenticationManager` is the single entry point for the authentication process in Spring Security. Its interface has one method:

```java
Authentication authenticate(Authentication authentication) throws AuthenticationException;
```

You pass in an "unauthenticated" token (username + raw password). It delegates to the configured provider(s). If verification succeeds, it returns a fully authenticated token. If it fails, it throws an `AuthenticationException`.

## What is `ProviderManager`?

`ProviderManager` is the standard implementation of `AuthenticationManager`. It holds a list of `AuthenticationProvider` objects and tries each one in order until one succeeds or all fail. In our setup, the list contains exactly one provider: the `DaoAuthenticationProvider` we configured.

---

## Step 1: Create file `SecurityConfig.java`

Path: `backend/auth-service/src/main/java/com/herafi/auth_service/config/SecurityConfig.java`

## Step 2: Paste this exactly:

```java
package com.herafi.auth_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(provider);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/register",
                                "/auth/login",
                                "/actuator/health"
                        ).permitAll()
                        .anyRequest().denyAll()
                )
                .build();
    }
}
```

The filter chain permits three paths with no authentication — register, login, and the health check — and **denies everything else**. Auth-service is not a general-purpose service; these three endpoints are all it should ever expose.

---

## The complete Spring Security login flow

When a login request arrives, this is the exact chain of calls that happens:

```
LoginRequest (POST /auth/login)
  |
  v
AuthenticationService.authenticate()
  |
  |  creates UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword)
  v
AuthenticationManager.authenticate()          <-- entry point
  |
  v
ProviderManager                               <-- tries each provider in order
  |
  v
DaoAuthenticationProvider                     <-- handles UsernamePasswordAuthenticationToken
  |
  |  calls loadUserByUsername(email)
  v
CustomUserDetailsService                      <-- goes to auth_db
  |
  |  SELECT * FROM user_credentials WHERE email = ?
  v
auth_db (PostgreSQL)
  |
  |  returns UserCredential row
  v
UserPrincipal.from(credential)                <-- wraps it as UserDetails
  |
  v
DaoAuthenticationProvider
  |
  |  calls BCryptPasswordEncoder.matches(rawPassword, userPrincipal.getPassword())
  v
BCrypt comparison
  |
  |  if mismatch -> throws BadCredentialsException
  |  if match    -> returns authenticated UsernamePasswordAuthenticationToken
  v
AuthenticationService
  |
  |  extracts UserPrincipal, loads UserCredential by ID from DB
  v
returns UserCredential to the controller
  |
  v
JwtService.generateAccessToken(credential)    <-- produces signed JWT string
  |
  v
LoginResponse (access_token, expires_in)      <-- sent back to client
```

Every step in this chain is handled by a separate class with a single responsibility. The `SecurityConfig` wires them together without any class needing to know about the others.

---

# Part 14: Represent the logged-in user with `UserPrincipal`

## What is `UserDetails`?

`UserDetails` is the contract Spring Security uses for a loaded user. It is an interface with methods like `getUsername()`, `getPassword()`, `getAuthorities()`, and flags like `isAccountNonExpired()`. When `DaoAuthenticationProvider` needs to compare the submitted password against the stored one, it calls `userDetails.getPassword()`. When it needs to build the authentication token, it calls `userDetails.getAuthorities()`.

Spring Security does not know or care about your `UserCredential` JPA entity. It only knows `UserDetails`. The `UserPrincipal` class bridges your domain entity to Spring Security's contract.

## Why not use `UserCredential` directly?

### Why do we need UserDetails? Why not use UserCredential directly with Spring Security?

`UserCredential` is a JPA entity: it has Hibernate lifecycle annotations, lazy-loaded relationships, and is tied to a database transaction. Handing it to the Spring Security layer would mean:

1. The security layer takes a dependency on your persistence layer — a layer violation.
2. JPA entities should not be passed around beyond the transaction boundary; Spring Security holds the authentication object for the lifetime of the request.
3. `UserCredential` would need to implement `UserDetails`, adding Spring Security imports to your entity — mixing concerns.

`UserPrincipal` is a plain Java `record`. It copies out exactly the fields Spring Security needs (id, email, passwordHash, role) and implements the interface cleanly. It is immutable by definition (a `record`), which is correct — a loaded user object should not change.

---

## Step 1: Create file `UserPrincipal.java`

Path: `backend/auth-service/src/main/java/com/herafi/auth_service/security/UserPrincipal.java`

## Step 2: Paste this exactly:

```java
package com.herafi.auth_service.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.herafi.auth_service.entity.Role;
import com.herafi.auth_service.entity.UserCredential;

public record UserPrincipal(
        UUID id,
        String email,
        String passwordHash,
        Role role
) implements UserDetails {

    public static UserPrincipal from(UserCredential credential) {
        return new UserPrincipal(
                credential.getId(),
                credential.getEmail(),
                credential.getPasswordHash(),
                credential.getRole()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
```

The `from()` factory method is a clean conversion point. Every place in the code that needs a `UserPrincipal` calls this one method. If `UserCredential` gains new fields, you update `from()` and nowhere else.

The `getAuthorities()` method wraps the role enum in a `SimpleGrantedAuthority` with the standard Spring Security `ROLE_` prefix. If the role is `USER`, the authority is `ROLE_USER`.

The default implementations of `isAccountNonExpired()`, `isAccountNonLocked()`, `isCredentialsNonExpired()`, and `isEnabled()` in the `UserDetails` interface all return `true`, which is appropriate since we have no account-locking logic.

---

# Part 15: Load users from the database with `CustomUserDetailsService`

## What is `UserDetailsService`?

`UserDetailsService` is a single-method interface:

```java
UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;
```

`DaoAuthenticationProvider` calls this method when it needs to verify a login. The "username" in Spring Security's terminology means whatever identifier you use to look up the user — in our case, the email address.

## What is `@Service`?

`@Service` is a specialization of `@Component`. Both tell Spring to create an instance of the class and register it in the container. The difference is semantic: `@Service` communicates that this class contains business logic. Spring itself treats `@Service` and `@Component` identically, but the distinction matters for code readability and for annotation-based tooling (like aspect-oriented proxies for transactions).

---

## Step 1: Create file `CustomUserDetailsService.java`

Path: `backend/auth-service/src/main/java/com/herafi/auth_service/security/CustomUserDetailsService.java`

## Step 2: Paste this exactly:

```java
package com.herafi.auth_service.security;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.herafi.auth_service.repository.UserCredentialRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserCredentialRepository repository;

    public CustomUserDetailsService(UserCredentialRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        return repository.findByEmail(normalizedEmail)
                .map(UserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
    }
}
```

Two important details:

1. **Normalization.** The email is trimmed and lowercased before the database query. This matches the normalization applied at registration time, ensuring that `User@Example.COM` and `user@example.com` resolve to the same account.

2. **Generic error message.** The exception message is `"Invalid email or password"`, not `"User not found"`. This is a security practice called **error message generalization** — if you distinguish between "user not found" and "wrong password", an attacker can enumerate valid email addresses. A generic message leaks nothing.

---

# Part 16: Handle user registration with `RegistrationService`

## What is `@Transactional`?

`@Transactional` wraps a method in a database **transaction**. A transaction is a unit of work that follows the ACID properties. The most important one here is **Atomicity**: either every operation in the method succeeds and is committed, or an exception causes the entire unit to be rolled back.

Spring implements `@Transactional` by wrapping your service class in a proxy. When you call `registrationService.register(...)`, you are actually calling a proxy method that:
1. Opens a database connection and begins a transaction.
2. Calls your actual `register()` method.
3. If the method returns normally — commits the transaction.
4. If the method throws an unchecked exception (`RuntimeException` or a subclass) — rolls back the transaction.

## What does `saveAndFlush` do vs `save`?

`save()` marks the entity as pending in Hibernate's first-level cache (the persistence context). The actual `INSERT` SQL may not run until the transaction commits or until Hibernate decides to flush.

`saveAndFlush()` forces an immediate `INSERT` SQL to be sent to the database, while still remaining inside the open transaction. We need this here because after saving the credential, we need to call user-service with the generated `UUID` (`savedCredential.getId()`). If we used plain `save()`, the ID might not be assigned yet depending on when Hibernate chooses to flush.

### Why is registration `@Transactional`? What rolls back if user-service fails?

Consider what happens without `@Transactional` if user-service is down:

1. auth-service saves `UserCredential` to `auth_db` — a credential row is committed permanently.
2. The HTTP call to user-service fails with a `RestClientException`.
3. The user now has an entry in `auth_db` but no profile in `user_db`. They can never log in successfully because other parts of the system assume a profile exists.

With `@Transactional`:

1. auth-service saves `UserCredential` (using `saveAndFlush` — the SQL runs but is NOT yet committed).
2. The HTTP call to user-service fails — `UserProfileClient` throws `UserServiceUnavailableException`, which extends `RuntimeException`.
3. Spring's transactional proxy catches this, **rolls back** the transaction.
4. The `UserCredential` row is erased as if it was never written.
5. The database remains consistent.

This is the guarantee: you never have half-registered users in your system.

---

## Step 1: Create file `RegistrationService.java`

Path: `backend/auth-service/src/main/java/com/herafi/auth_service/service/RegistrationService.java`

## Step 2: Paste this exactly:

```java
package com.herafi.auth_service.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.auth_service.dto.RegisterRequest;
import com.herafi.auth_service.dto.CreateUserProfileRequest;
import com.herafi.auth_service.entity.Role;
import com.herafi.auth_service.entity.UserCredential;
import com.herafi.auth_service.exception.EmailAlreadyInUseException;
import com.herafi.auth_service.repository.UserCredentialRepository;

@Service
public class RegistrationService {

    private final UserCredentialRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final UserProfileClient userProfileClient;

    public RegistrationService(
            UserCredentialRepository repository,
            PasswordEncoder passwordEncoder,
            UserProfileClient userProfileClient) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.userProfileClient = userProfileClient;
    }

    @Transactional
    public UserCredential register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (repository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException();
        }

        UserCredential credential = new UserCredential();
        credential.setId(UUID.randomUUID());
        credential.setEmail(email);
        credential.setPasswordHash(passwordEncoder.encode(request.password()));
        credential.setRole(Role.USER);

        UserCredential savedCredential = repository.saveAndFlush(credential);

        userProfileClient.createProfile(new CreateUserProfileRequest(
                savedCredential.getId(),
                savedCredential.getEmail(),
                request.firstName().trim(),
                request.lastName().trim()
        ));

        return savedCredential;
    }
}
```

Read through the method step by step:

1. Normalize the email — same rule as `CustomUserDetailsService`, always lowercase.
2. Reject duplicate emails before doing any work.
3. Build the `UserCredential` entity. We assign the `UUID` manually with `UUID.randomUUID()` rather than relying on the database sequence, because we need the ID before flushing (and because UUIDs are better distributed across services than auto-increment integers).
4. Hash the password with `passwordEncoder.encode()`. The plain-text password never touches the database.
5. `saveAndFlush` — forces the INSERT so we have a confirmed, database-assigned entity.
6. Call user-service. If this throws, the whole transaction rolls back.

---

# Part 17: Verify credentials with `AuthenticationService`

## What is `UsernamePasswordAuthenticationToken.unauthenticated()`?

`UsernamePasswordAuthenticationToken` is a class that implements `Authentication`. It can represent two states:

- **Unauthenticated**: holds a username (principal) and raw password (credentials). The `isAuthenticated()` flag is `false`. This is what you pass *in* to `AuthenticationManager`.
- **Authenticated**: holds a `UserDetails` object as the principal, has the credentials erased, and has `isAuthenticated()` set to `true`. This is what `DaoAuthenticationProvider` returns *after* successful verification.

The static factory `UsernamePasswordAuthenticationToken.unauthenticated(principal, credentials)` creates the first form. Using the factory is better than calling the constructor directly because it is explicit about the intent: this token has not been verified yet.

---

## Step 1: Create file `AuthenticationService.java`

Path: `backend/auth-service/src/main/java/com/herafi/auth_service/service/AuthenticationService.java`

## Step 2: Paste this exactly:

```java
package com.herafi.auth_service.service;

import java.util.Locale;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import com.herafi.auth_service.dto.LoginRequest;
import com.herafi.auth_service.entity.UserCredential;
import com.herafi.auth_service.exception.InvalidCredentialsException;
import com.herafi.auth_service.repository.UserCredentialRepository;
import com.herafi.auth_service.security.UserPrincipal;

@Service
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final UserCredentialRepository repository;

    public AuthenticationService(
            AuthenticationManager authenticationManager,
            UserCredentialRepository repository) {
        this.authenticationManager = authenticationManager;
        this.repository = repository;
    }

    public UserCredential authenticate(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            email,
                            request.password()
                    )
            );

            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            return repository.findById(principal.id())
                    .orElseThrow(InvalidCredentialsException::new);
        } catch (AuthenticationException exception) {
            throw new InvalidCredentialsException();
        }
    }
}
```

After `authenticationManager.authenticate()` returns, we have a fully authenticated `Authentication` object. We cast `getPrincipal()` to `UserPrincipal` (our `UserDetails` implementation) and use the stored `UUID` to reload the `UserCredential` entity from the database. We do this reload rather than calling `UserPrincipal.toCredential()` because:

1. `UserPrincipal` intentionally does not carry every field from `UserCredential` — only what Spring Security needs.
2. A second database read at this point is negligible cost and keeps the two layers strictly separate.

Any Spring Security failure — wrong password, user not found, account locked — throws a subtype of `AuthenticationException`. We catch all of them and rethrow as our own `InvalidCredentialsException`, again to avoid leaking which specific check failed.

---

# Part 18: Issue signed JWTs with `JwtService`

## What are JWT claims?

A JWT has three parts separated by dots: `header.payload.signature`. The **payload** is a JSON object containing **claims** — statements about the subject. Some claims are standardized by the JWT RFC (RFC 7519):

| Claim | Name       | What it holds                                   |
|-------|------------|-------------------------------------------------|
| `sub` | Subject    | Who the token is about — the user's UUID        |
| `iss` | Issuer     | Who created the token — `"herafi-auth-service"` |
| `iat` | Issued At  | Unix timestamp of when the token was created    |
| `exp` | Expiry     | Unix timestamp after which the token is invalid |
| `jti` | JWT ID     | A unique ID for this token (prevents replay)    |

Beyond these, you can add custom claims. We add `email` and `role` so that other services can read them directly from the token without going back to auth-service.

## What is `JwtClaimsSet`?

`JwtClaimsSet` is Spring Security's builder-based representation of the payload. You set each claim through a typed method (`subject()`, `issuer()`, `expiresAt()`) or through the generic `claim(name, value)` for custom ones. It enforces that `sub`, `iss`, and `exp` are present, which are the minimum required fields.

## What is `JwsHeader`?

JWS stands for **JSON Web Signature**. `JwsHeader` represents the JWT header, which declares the algorithm used to sign the payload. We build it with `JwsHeader.with(MacAlgorithm.HS256).build()`. When `JwtEncoder` receives both the header and the claims set, it signs the payload using the secret key and returns the final `header.payload.signature` string.

### Why does `JwtService` take a `UserCredential` not a `UserPrincipal`?

Both objects carry the same fields we need (`id`, `email`, `role`). But `JwtService` is in the `service` layer and `UserPrincipal` is in the `security` layer. Allowing a service class to depend on a security-layer class creates a dependency that flows in the wrong direction. Services should not know about Spring Security's internal representation of a user.

`UserCredential` is the domain entity — the source of truth for auth-service's persistence layer. By taking `UserCredential`, `JwtService` depends only on the domain, not on any framework contract. If you later remove Spring Security and use a different security library, `JwtService` requires no changes.

---

## Step 1: Create file `JwtService.java`

Path: `backend/auth-service/src/main/java/com/herafi/auth_service/service/JwtService.java`

## Step 2: Paste this exactly:

```java
package com.herafi.auth_service.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import com.herafi.auth_service.entity.UserCredential;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final Duration accessTokenTtl;
    private final String issuer;

    public JwtService(
            JwtEncoder jwtEncoder,
            @Value("${security.jwt.access-token-ttl}") Duration accessTokenTtl,
            @Value("${security.jwt.issuer}") String issuer) {
        this.jwtEncoder = jwtEncoder;
        this.accessTokenTtl = accessTokenTtl;
        this.issuer = issuer;
    }

    public String generateAccessToken(UserCredential credential) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(credential.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("email", credential.getEmail())
                .claim("role", credential.getRole().name())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }
}
```

`getAccessTokenTtlSeconds()` is a utility method used by the controller to include an `expires_in` field in the login response, so clients know how long the token is valid without parsing it themselves.

The `jti` claim (`id()`) is a random UUID per token. Even if two tokens are issued for the same user in the same second, they will have different IDs. This is the foundation for token revocation if you ever add a blocklist.

---

# Part 19: Call user-service internally with `UserProfileClient`

## Why send `X-Internal-Api-Key` when calling user-service?

User-service exposes `/internal/users` — an endpoint that creates a user profile directly, bypassing normal user-facing validation. This endpoint must never be reachable by the public. It is not behind a JWT because at the moment of registration, no JWT exists yet.

The solution is a **shared secret** (the internal API key) that only services running inside the private network know. User-service reads the `X-Internal-Api-Key` header on every request to `/internal/**` and rejects any call that does not present the correct value. Auth-service fetches this key from its own environment configuration and attaches it to every request.

This pattern is called **service-to-service authentication**. The security model is:

- The `/internal/users` endpoint is not exposed through the API gateway to the public internet — only accessible within the Docker network.
- Even if somehow reachable, the caller must know the secret key.
- The key is stored in environment variables, not in source code.

---

## Step 1: Create file `UserProfileClient.java`

Path: `backend/auth-service/src/main/java/com/herafi/auth_service/service/UserProfileClient.java`

## Step 2: Paste this exactly:

```java
package com.herafi.auth_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.herafi.auth_service.dto.CreateUserProfileRequest;
import com.herafi.auth_service.exception.UserServiceUnavailableException;

@Service
public class UserProfileClient {

    private final RestClient restClient;
    private final String internalApiKey;

    public UserProfileClient(
            RestClient userServiceRestClient,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.restClient = userServiceRestClient;
        this.internalApiKey = internalApiKey;
    }

    public void createProfile(CreateUserProfileRequest request) {
        try {
            restClient.post()
                    .uri("/internal/users")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new UserServiceUnavailableException(exception);
        }
    }
}
```

Three things to note:

1. **Constructor parameter name matters.** The `RestClient` parameter is named `userServiceRestClient`. Spring matches this to the bean declared in `UserServiceClientConfig` by name. If you have multiple `RestClient` beans and the parameter is named differently, Spring will fail with a `NoUniqueBeanDefinitionException`.

2. **`toBodilessEntity()`** tells `RestClient` that we do not care about the response body — we only care that user-service returned a 2xx status. If user-service returns a 4xx or 5xx, `RestClient` throws a `RestClientException`.

3. **Error translation.** We catch `RestClientException` (any HTTP or connection failure) and rethrow it as `UserServiceUnavailableException`. This keeps the service layer's exception type meaningful to callers, and — because `UserServiceUnavailableException` extends `RuntimeException` — it triggers the `@Transactional` rollback in `RegistrationService`, unwinding the credential write as explained in Part 16.
# Part 20: AuthController

## Concepts Before the Code

### What @RestController Does

`@RestController` is a composed annotation — it combines two things into one:

- `@Controller` — tells Spring that this class handles HTTP requests
- `@ResponseBody` — tells Spring to serialize every return value directly to the HTTP response body as JSON (rather than treating the return value as the name of a view template)

Before `@RestController` existed, you had to put `@ResponseBody` on every method individually. Using `@RestController` means you declare it once at the class level and Spring applies it to all methods automatically.

### What @RequestMapping at the Class Level Does

When you place `@RequestMapping("/auth")` on the class, you are declaring a base URL prefix. Every method inside the class inherits that prefix. So if the class has `@RequestMapping("/auth")` and a method has `@PostMapping("/register")`, the full URL for that method is `/auth/register`.

This avoids repeating the prefix on every single method and makes it easy to change the base path in one place.

### What @PostMapping Does

`@PostMapping("/register")` is shorthand for `@RequestMapping(value = "/register", method = RequestMethod.POST)`. It binds the method to HTTP POST requests at that path. The HTTP method matters because the same URL can serve different operations depending on the verb — POST to create, GET to read, PUT to update, DELETE to remove.

### What @RequestBody Does

`@RequestBody` tells Spring to read the HTTP request body, deserialize it from JSON, and bind the result to the method parameter. Without this annotation, Spring would ignore the request body entirely.

### What @Valid Triggers

When you annotate a `@RequestBody` parameter with `@Valid`, Spring runs the Bean Validation framework against all constraint annotations on the DTO before your method body executes. If any constraint fails (a required field is missing, a string is too long, an email address is malformed), Spring throws a `MethodArgumentNotValidException` instead of calling your method at all. This means your service layer never sees invalid data.

### What ResponseEntity Is

`ResponseEntity<T>` is a wrapper that gives you full control over the HTTP response:

- The response body (type `T`)
- The HTTP status code (200, 201, 401, 409, etc.)
- Response headers (if you need them)

When you return a plain object from a controller method, Spring always uses status 200. When you return a `ResponseEntity`, you choose the status explicitly. For example, a successful resource creation should return 201 Created, not 200 OK — and `ResponseEntity.status(HttpStatus.CREATED).body(...)` expresses that clearly.

### Why Does AuthController Not Contain Business Logic?

The controller's job is exactly one thing: translate between HTTP and your application. It reads the HTTP request, calls a service, and writes the HTTP response. Business logic — checking whether an email is already taken, hashing passwords, talking to other services — lives in the service layer.

This separation matters for two reasons. First, it keeps each class small and focused, which makes them easier to read and change. Second, it means you can test the business logic independently of HTTP without spinning up a web server.

In `AuthController`, notice that there are zero `if` statements and zero database calls. Everything is delegated to `RegistrationService`, `AuthenticationService`, and `JwtService`.

## Step 1: Create the controller file

**Path:** `backend/auth-service/src/main/java/com/herafi/auth_service/controller/AuthController.java`

## Step 2: Paste this exactly

```java
package com.herafi.auth_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.auth_service.dto.RegisterRequest;
import com.herafi.auth_service.dto.AuthResponse;
import com.herafi.auth_service.dto.LoginRequest;
import com.herafi.auth_service.entity.UserCredential;
import com.herafi.auth_service.service.AuthenticationService;
import com.herafi.auth_service.service.JwtService;
import com.herafi.auth_service.service.RegistrationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final JwtService jwtService;

    public AuthController(
            RegistrationService registrationService,
            AuthenticationService authenticationService,
            JwtService jwtService) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        UserCredential credential = registrationService.register(request);
        String accessToken = jwtService.generateAccessToken(credential);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(AuthResponse.from(
                        credential,
                        accessToken,
                        jwtService.getAccessTokenTtlSeconds()
                ));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        UserCredential credential = authenticationService.authenticate(request);
        String accessToken = jwtService.generateAccessToken(credential);

        return AuthResponse.from(
                credential,
                accessToken,
                jwtService.getAccessTokenTtlSeconds()
        );
    }
}
```

### Reading the code

**register:** Returns `ResponseEntity<AuthResponse>` so it can send back `201 Created`. The `@Valid` annotation on `@RequestBody RegisterRequest request` triggers constraint checking before the method body runs. If validation passes, it calls `RegistrationService.register()`, then immediately generates a JWT and returns it to the client so they are logged in straight after signing up.

**login:** Returns `AuthResponse` directly (no `ResponseEntity` wrapper) because a successful login always returns 200 OK and there is no status code to override. Spring serializes the return value to JSON automatically because the class is annotated with `@RestController`.

---

# Part 21: Exception Classes for auth-service

## Concepts Before the Code

### Why Separate Exception Classes Instead of RuntimeException Directly?

You could write `throw new RuntimeException("Email already in use")` anywhere in your code. It works. But there are two problems:

1. **You cannot catch by type.** The `GlobalExceptionHandler` uses `@ExceptionHandler(EmailAlreadyInUseException.class)` to route that specific exception to the right handler. If everything is a `RuntimeException`, every handler would need to inspect the message string to figure out what went wrong — fragile and messy.

2. **The exception becomes part of your API contract.** When `RegistrationService` is documented to throw `EmailAlreadyInUseException`, a reader immediately knows what can go wrong and why. `RuntimeException` tells them nothing.

Typed exceptions make routing automatic and make intent clear. The cost is a few extra files. The benefit is a system where exceptions flow to the right HTTP responses without any manual routing code.

## Step 1: Create EmailAlreadyInUseException.java

**Path:** `backend/auth-service/src/main/java/com/herafi/auth_service/exception/EmailAlreadyInUseException.java`

## Step 2: Paste this exactly

```java
package com.herafi.auth_service.exception;

public class EmailAlreadyInUseException extends RuntimeException {

    public EmailAlreadyInUseException() {
        super("Email already in use");
    }
}
```

## Step 3: Create InvalidCredentialsException.java

**Path:** `backend/auth-service/src/main/java/com/herafi/auth_service/exception/InvalidCredentialsException.java`

## Step 4: Paste this exactly

```java
package com.herafi.auth_service.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
```

## Step 5: Create UserServiceUnavailableException.java

**Path:** `backend/auth-service/src/main/java/com/herafi/auth_service/exception/UserServiceUnavailableException.java`

## Step 6: Paste this exactly

```java
package com.herafi.auth_service.exception;

public class UserServiceUnavailableException extends RuntimeException {

    public UserServiceUnavailableException(Throwable cause) {
        super("User profile service is unavailable", cause);
    }
}
```

### Reading the code

Notice that `UserServiceUnavailableException` accepts a `Throwable cause`. This is important: when `auth-service` tries to call `user-service` over HTTP and the call fails, it wraps the original network exception inside `UserServiceUnavailableException`. This preserves the root cause so that when the exception appears in your logs, you can see the original `IOException` or timeout that triggered it, not just the high-level message.

---

# Part 22: GlobalExceptionHandler for auth-service

## Concepts Before the Code

### What @RestControllerAdvice Does

`@RestControllerAdvice` marks a class as a global exception handler for all `@RestController` classes in the application. Any exception that escapes from any controller method — regardless of which controller threw it — will be caught here and converted to an HTTP response.

This is the alternative to wrapping every controller method in a try-catch block. With `@RestControllerAdvice`, you write the error-handling logic once and it applies everywhere automatically.

### What @ExceptionHandler Does

`@ExceptionHandler(SomeException.class)` inside a `@RestControllerAdvice` class declares a method that handles exceptions of that specific type. When Spring catches an exception, it looks through all registered `@ExceptionHandler` methods to find the best match by type and calls it with the exception instance. The return value of the handler method becomes the HTTP response, just like a normal controller method.

### Why @RestControllerAdvice Instead of Try-Catch in Every Controller?

Consider what happens without a global handler: every controller method needs a try-catch, the catch blocks all need to construct `ResponseEntity` objects with the right status codes and error bodies, and you repeat the same patterns dozens of times. When the error format changes, you update dozens of places.

With `@RestControllerAdvice`, all that logic lives in one file. Controllers stay focused on the happy path. Error-handling logic is centralized, testable, and consistent.

### What MethodArgumentNotValidException Is

When `@Valid` on a `@RequestBody` parameter fails — meaning one or more constraint annotations on the DTO were violated — Spring throws `MethodArgumentNotValidException` automatically. The exception carries a `BindingResult` that contains the full list of field errors, including which field failed and why. The handler below extracts that list and returns it as structured JSON so the client knows exactly which fields to fix.

### HTTP Status Codes Used Here

| Status | Meaning |
|---|---|
| `HttpStatus.CONFLICT` (409) | The request conflicts with existing data — for example, the email is already registered |
| `HttpStatus.UNAUTHORIZED` (401) | Authentication failed — the credentials were wrong |
| `HttpStatus.SERVICE_UNAVAILABLE` (503) | A downstream service we depend on is not reachable |
| `400 Bad Request` | The request itself is malformed — validation errors |

## Step 1: Create GlobalExceptionHandler.java

**Path:** `backend/auth-service/src/main/java/com/herafi/auth_service/exception/GlobalExceptionHandler.java`

## Step 2: Paste this exactly

```java
package com.herafi.auth_service.exception;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.herafi.auth_service.dto.ApiErrorResponse;
import com.herafi.auth_service.dto.ApiErrorResponse.FieldValidationError;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({EmailAlreadyInUseException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiErrorResponse> handleEmailConflict(Exception exception) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("EMAIL_ALREADY_IN_USE", "Email already in use"));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException exception) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of("INVALID_CREDENTIALS", exception.getMessage()));
    }

    @ExceptionHandler(UserServiceUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleUserServiceUnavailable(
            UserServiceUnavailableException exception) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiErrorResponse.of("USER_SERVICE_UNAVAILABLE", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception) {
        List<FieldValidationError> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldValidationError(
                        error.getField(),
                        error.getDefaultMessage()
                ))
                .toList();

        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.validation(errors));
    }
}
```

### Reading the code

**handleEmailConflict:** This handler catches both `EmailAlreadyInUseException` (our custom exception) and Spring's `DataIntegrityViolationException` (thrown when a database unique constraint is violated at the SQL level). This is defensive programming — even if somehow a duplicate email slips past our application-level check and hits the database, the response is still `409 CONFLICT` with the correct error code.

**handleValidation:** The `.getBindingResult().getFieldErrors()` call retrieves the list of all fields that failed validation. We map each one to a `FieldValidationError` record and include all of them in the response body. This means a client that submits a form with three invalid fields gets all three errors back in a single response, instead of having to submit and fix one error at a time.

---

# Part 23: application.properties for auth-service

## Concepts Before the Code

### What application.properties Is

`application.properties` is the main configuration file for a Spring Boot application. Spring Boot reads it automatically at startup. Every line is either a Spring framework property (configuring things like database connections, JPA behaviour, server port) or a custom property your own code reads via `@Value("${property.name}")`.

### The ${VAR:default} Syntax

The pattern `${ENV_VAR:fallback-value}` means: read the environment variable `ENV_VAR`. If it is not set, use `fallback-value` instead. This is how the same application.properties file works both locally during development (using the default values) and in production (using environment variables injected by your deployment system, Docker, or Kubernetes).

For example:
```
server.port=${SERVER_PORT:8081}
```
In development, the port is 8081. In a Docker container where `SERVER_PORT=9090` is set, the port is 9090.

## Step 1: Create application.properties

**Path:** `backend/auth-service/src/main/resources/application.properties`

## Step 2: Paste this exactly

```properties
spring.application.name=auth-service
server.port=${SERVER_PORT:8081}
spring.autoconfigure.exclude=org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration

spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/auth_db}
spring.datasource.username=${DB_USERNAME:herafi}
spring.datasource.password=${DB_PASSWORD:herafi}

spring.jpa.hibernate.ddl-auto=update
spring.jpa.open-in-view=false
spring.jpa.show-sql=${SHOW_SQL:true}

security.jwt.secret=${JWT_SECRET:7irafi-development-jwt-secret-change-me-2026}
security.jwt.issuer=${JWT_ISSUER:7irafi-auth-service}
security.jwt.access-token-ttl=${JWT_ACCESS_TOKEN_TTL:15m}

services.user-service.url=${USER_SERVICE_URL:http://localhost:8082}
services.internal-api-key=${INTERNAL_API_KEY:7irafi-development-internal-key}

management.endpoints.web.exposure.include=health
```

### Reading the config

**spring.autoconfigure.exclude:** We exclude Spring Security's `UserDetailsServiceAutoConfiguration` because this service does its own authentication from scratch (checking bcrypt hashes) and does not use Spring Security's built-in user details system.

**spring.jpa.hibernate.ddl-auto=update:** In development, Hibernate inspects the entity classes and creates or updates database tables automatically. In production you would change this to `validate` or `none` and manage schema changes with a migration tool.

**spring.jpa.open-in-view=false:** Disables a Spring default that keeps a database connection open for the entire duration of an HTTP request. This is a well-known performance anti-pattern. Setting it to false ensures connections are only held while a transaction is actually running.

**security.jwt.*:** These are our own custom properties, read by `JwtService` using `@Value`. The defaults are suitable for local development but must be overridden with strong secrets in production.

**management.endpoints.web.exposure.include=health:** Exposes only the `/actuator/health` endpoint (useful for Docker health checks and load balancers). All other actuator endpoints remain private.

---

# Part 24: application-test.properties for auth-service

## Concepts Before the Code

### Spring Profiles

Spring Boot supports the concept of profiles — named sets of configuration that can be activated at runtime. When a test class is annotated with `@ActiveProfiles("test")`, Spring loads `application.properties` as usual but then also loads `application-test.properties` and merges it, with the test file taking precedence on any property that appears in both.

This is how the same application can connect to PostgreSQL in production and to H2 in tests, without any code changes — only configuration changes.

### H2 In-Memory Database

H2 is a lightweight Java database that runs entirely in memory. It is included in the project as a `test`-scoped Maven dependency. When tests run, H2 starts instantly, the schema is created from your JPA entities, tests run against it, and the database disappears when the JVM exits. This makes tests fast (no network round trip), isolated (each test run starts clean), and portable (no PostgreSQL installation required on the developer's machine or CI server).

The connection URL `jdbc:h2:mem:auth_service_test;MODE=PostgreSQL` tells H2 to emulate PostgreSQL syntax and behaviour as closely as possible, which reduces the chance of queries that work in tests but fail in production.

## Step 1: Create application-test.properties

**Path:** `backend/auth-service/src/test/resources/application-test.properties`

## Step 2: Paste this exactly

```properties
spring.datasource.url=jdbc:h2:mem:auth_service_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=create-drop

spring.jpa.show-sql=false

security.jwt.secret=7irafi-test-jwt-secret-that-is-at-least-32-bytes
security.jwt.issuer=7irafi-auth-service-test
security.jwt.access-token-ttl=15m

services.user-service.url=http://localhost:9999
services.internal-api-key=test-internal-key
```

### Reading the config

**spring.jpa.hibernate.ddl-auto=create-drop:** In tests, the schema is created fresh at the start of the test run and dropped at the end. Unlike `update` (which modifies an existing schema), `create-drop` guarantees a clean slate every time.

**DB_CLOSE_DELAY=-1:** Tells H2 to keep the in-memory database alive for as long as the JVM is running. Without this, H2 would destroy the database the moment the last connection closed, which can cause failures in tests that open multiple connections.

**services.user-service.url=http://localhost:9999:** Points to a port that nothing is listening on. Tests that need to verify the failure path when `user-service` is unavailable will correctly receive a connection refused error from this address.

**spring.jpa.show-sql=false:** Suppresses SQL logging during tests to keep the console output readable.

---

# Part 25: auth-service Test

## Concepts Before the Code

### What @SpringBootTest Does

`@SpringBootTest` loads the complete Spring application context — every bean, every configuration class, every auto-configuration. This is an integration test: it tests the real system, not a mocked version of it.

For a smoke test like the one below, the goal is simply to verify that the application starts up correctly. If any bean fails to initialize (a misconfigured property, a missing dependency, a database connection error), the test fails immediately, which is exactly what you want to catch in CI.

### What @ActiveProfiles("test") Does

Activates the `test` Spring profile, which causes Spring to load `application-test.properties` and switch the database to H2. Without this annotation, `@SpringBootTest` would try to connect to a real PostgreSQL database and fail.

## Step 1: Create the test file

**Path:** `backend/auth-service/src/test/java/com/herafi/auth_service/AuthServiceApplicationTests.java`

## Step 2: Paste this exactly

```java
package com.herafi.auth_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
```

### Reading the code

The test method body is empty. The test passes if the application context loads without throwing an exception and fails if anything prevents Spring from starting up. This is called a smoke test or context load test. It is one of the most valuable tests you can have because it catches broken configuration, missing beans, and wiring errors instantly.

---

# Part 26: user-service pom.xml and UserServiceApplication.java

## What This Service Does

`user-service` is responsible for one thing: storing and serving user profile data (name, email, timestamps). It does not handle passwords, JWTs, or authentication. It trusts that the gateway and auth-service have already verified who the caller is.

## Step 1: Create pom.xml

**Path:** `backend/user-service/pom.xml`

## Step 2: Paste this exactly

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>

    <groupId>com.herafi</groupId>
    <artifactId>user-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>user-service</name>
    <description>User profile service for 7irafi</description>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### Reading the pom.xml

The dependencies are identical to `auth-service` with one notable absence: there is no JWT library. `user-service` never parses or validates JWTs. It receives the user's identity pre-extracted from a request header (`X-User-Id`) that the gateway populated after verifying the token. This is a deliberate design choice explored further in Part 32.

Lombok is included and configured both as a build dependency (so the annotation processor runs) and excluded from the final JAR (so it does not ship at runtime, since it does all its work at compile time).

## Step 3: Create UserServiceApplication.java

**Path:** `backend/user-service/src/main/java/com/herafi/user_service/UserServiceApplication.java`

## Step 4: Paste this exactly

```java
package com.herafi.user_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
```

---

# Part 27: UserProfile Entity

## Concepts Before the Code

### Why No @GeneratedValue? Who Creates the ID?

In most JPA entities you see `@Id @GeneratedValue(strategy = GenerationType.UUID)` — this tells Hibernate to generate a new UUID for each entity before inserting it into the database.

`UserProfile` deliberately omits `@GeneratedValue`. There is only `@Id` and no generation strategy. The reason is that the ID of a user profile must be the same UUID that was assigned to the user by `auth-service` when the account was created. The `auth-service` creates a `UserCredential` row in its own database with a generated UUID. It then calls `user-service` to create a matching profile, passing that same UUID in the request body. `user-service` trusts and stores that UUID directly, with no modification.

This keeps the two services in sync without any shared database or synchronous lookup: wherever you have a user's UUID (from the JWT, from a session, from anywhere), you can query either service for data about that user without any joining or cross-service lookup.

If `user-service` generated its own IDs, you would need an additional field mapping auth IDs to profile IDs, which complicates every lookup.

### @PrePersist and @PreUpdate

These are JPA lifecycle callback annotations:

- `@PrePersist` — the annotated method runs automatically just before the entity is inserted into the database for the first time
- `@PreUpdate` — the annotated method runs automatically just before the entity is updated in the database

Using these to set timestamps is better than setting them manually in service code because it works regardless of how the entity is saved — even if someone bypasses the service layer and uses the repository directly.

## Step 1: Create UserProfile.java

**Path:** `backend/user-service/src/main/java/com/herafi/user_service/entity/UserProfile.java`

## Step 2: Paste this exactly

```java
package com.herafi.user_service.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 50)
    private String firstName;

    @Column(nullable = false, length = 50)
    private String lastName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
```

### Reading the code

**Instant vs LocalDateTime:** `Instant` represents a specific point in time with no timezone attached (it stores UTC nanoseconds since the Unix epoch). `LocalDateTime` stores a date and time without any timezone information at all. For timestamps in a distributed system, `Instant` is always the right choice: it is unambiguous, sortable, and works correctly regardless of the server's configured timezone.

**updatable = false on createdAt:** The `@Column(updatable = false)` attribute tells Hibernate to never include `createdAt` in `UPDATE` SQL statements. Even if application code accidentally sets it, the value in the database will not change after the initial insert.

**@Getter, @Setter, @NoArgsConstructor:** Lombok generates all getters, all setters, and a no-argument constructor at compile time. JPA requires a no-argument constructor to instantiate entity instances when reading from the database.

---

# Part 28: UserProfileRepository

## Step 1: Create UserProfileRepository.java

**Path:** `backend/user-service/src/main/java/com/herafi/user_service/repository/UserProfileRepository.java`

## Step 2: Paste this exactly

```java
package com.herafi.user_service.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.herafi.user_service.entity.UserProfile;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    boolean existsByEmail(String email);
}
```

### Reading the code

`JpaRepository<UserProfile, UUID>` provides standard CRUD methods: `save`, `findById`, `existsById`, `findAll`, `deleteById`, and many more. You get all of them for free just by declaring the interface.

`existsByEmail(String email)` is a derived query method. Spring Data JPA reads the method name and generates the SQL `SELECT COUNT(*) > 0 FROM user_profiles WHERE email = ?` automatically. No SQL, no JPQL, no implementation code required. The naming convention is `existsBy` followed by the exact field name.

This method is used in `UserProfileService` to check whether an email is already taken before inserting a new profile.

---

# Part 29: user-service DTOs

## Concepts Before the Code

### Why Separate Request and Response DTOs?

A DTO (Data Transfer Object) is a simple data carrier with no behaviour. Using separate request and response DTOs instead of exposing entity objects directly has two benefits:

1. **Decoupling the API from the database.** You can rename a database column without changing the API, or change the API shape without touching the database.
2. **Controlled exposure.** The entity might have fields (like internal audit fields or sensitive data) that should never leave the service. The response DTO is an explicit list of what you choose to share.

Java records are ideal for DTOs: they are immutable by default, have automatically generated constructors, equals, hashCode, and toString, and are very concise.

## Step 1: Create CreateUserProfileRequest.java

**Path:** `backend/user-service/src/main/java/com/herafi/user_service/dto/CreateUserProfileRequest.java`

## Step 2: Paste this exactly

```java
package com.herafi.user_service.dto;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserProfileRequest(
        @NotNull
        UUID id,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(max = 50)
        String firstName,

        @NotBlank
        @Size(max = 50)
        String lastName
) {
}
```

## Step 3: Create UpdateUserProfileRequest.java

**Path:** `backend/user-service/src/main/java/com/herafi/user_service/dto/UpdateUserProfileRequest.java`

## Step 4: Paste this exactly

```java
package com.herafi.user_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @NotBlank
        @Size(max = 50)
        String firstName,

        @NotBlank
        @Size(max = 50)
        String lastName
) {
}
```

## Step 5: Create UserProfileResponse.java

**Path:** `backend/user-service/src/main/java/com/herafi/user_service/dto/UserProfileResponse.java`

## Step 6: Paste this exactly

```java
package com.herafi.user_service.dto;

import java.time.Instant;
import java.util.UUID;

import com.herafi.user_service.entity.UserProfile;

public record UserProfileResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        Instant createdAt,
        Instant updatedAt
) {

    public static UserProfileResponse from(UserProfile profile) {
        return new UserProfileResponse(
                profile.getId(),
                profile.getEmail(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
```

## Step 7: Create ApiResponse.java

**Path:** `backend/user-service/src/main/java/com/herafi/user_service/dto/ApiResponse.java`

## Step 8: Paste this exactly

```java
package com.herafi.user_service.dto;

public record ApiResponse<T>(
        boolean success,
        T data
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data);
    }
}
```

## Step 9: Create ApiErrorResponse.java

**Path:** `backend/user-service/src/main/java/com/herafi/user_service/dto/ApiErrorResponse.java`

## Step 10: Paste this exactly

```java
package com.herafi.user_service.dto;

import java.util.List;

public record ApiErrorResponse(
        boolean success,
        ErrorDetails error
) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(false, new ErrorDetails(code, message, List.of()));
    }

    public static ApiErrorResponse validation(List<FieldValidationError> errors) {
        return new ApiErrorResponse(
                false,
                new ErrorDetails("VALIDATION_ERROR", "Validation failed", errors)
        );
    }

    public record ErrorDetails(
            String code,
            String message,
            List<FieldValidationError> errors
    ) {
    }

    public record FieldValidationError(
            String field,
            String message
    ) {
    }
}
```

### Reading the code

`ApiResponse<T>` is a generic wrapper for successful responses. The `success: true` field is included so that clients can check the shape of any response uniformly, without relying on the HTTP status code alone. The `data` field holds the actual payload.

`ApiErrorResponse` mirrors this structure for failures: `success: false` and an `error` object containing a machine-readable `code`, a human-readable `message`, and an optional list of `FieldValidationError` objects for validation failures.

`UpdateUserProfileRequest` deliberately omits `id` and `email`. Users cannot change their own ID (that would break cross-service consistency) or their email address via a simple profile update (email changes typically require re-verification and go through a separate flow).

---

# Part 30: TrustedCallerFilter

## Concepts Before the Code

### What OncePerRequestFilter Is

`OncePerRequestFilter` is a Spring base class for servlet filters that guarantees `doFilterInternal` is called exactly once per HTTP request, even when requests are forwarded internally (which can cause normal filters to run multiple times). When you extend it, you implement `doFilterInternal` instead of the raw `doFilter` method.

A servlet filter runs before the request reaches any controller. This makes it the right place for cross-cutting concerns like authentication, authorization, and security checks — work that must happen regardless of which endpoint is being called.

### What MessageDigest.isEqual Does — And Why It Matters

The naive way to compare a submitted secret with the expected secret is:

```java
submittedKey.equals(expectedKey)
```

This has a subtle security flaw. Java's `String.equals` method is not constant-time — it returns `false` as soon as it finds the first differing character, without comparing the rest. An attacker can exploit this by measuring how long the comparison takes: a request that matches the first 10 characters takes slightly longer than one that matches zero characters. By sending thousands of requests and measuring response times, an attacker can recover the secret one character at a time. This is called a timing attack.

`MessageDigest.isEqual(byte[], byte[])` compares two byte arrays in constant time — it always checks every byte, regardless of where the first difference is. The comparison time reveals nothing about how many characters matched.

For any secret key comparison in security-sensitive code, always use constant-time comparison.

### Why Does user-service Not Parse the JWT Itself?

This is a deliberate architectural decision. The API gateway is the only service that receives requests from the public internet. The gateway verifies the JWT signature, extracts the claims, and injects them as HTTP headers (`X-User-Id`, `X-User-Role`) before forwarding the request to internal services.

Internal services — like `user-service` — trust those headers. They do not need to have the JWT secret, do not need the JWT library, and do not need to repeat verification logic. This has several benefits:

- JWT verification logic is in one place (the gateway), so any update to the algorithm or key rotation is done once
- Internal services are simpler — they focus entirely on their domain, not on authentication infrastructure
- A compromised internal service cannot forge JWTs even if it is breached, because it never had the secret

The `TrustedCallerFilter` is the security boundary that enforces this design: it rejects any request to `/users/*` that does not carry the shared gateway secret in `X-Gateway-Key`. This ensures that only the gateway can reach the user profile endpoints — and the gateway only sets `X-User-Id` after verifying the JWT. So by the time `UserProfileController` reads `@RequestHeader("X-User-Id")`, that value has already been authenticated.

## Error Code Reference Table

All error codes used across both services are listed below. This is the machine-readable contract between the backend and frontend:

| Error Code | HTTP Status | Thrown By | Meaning |
|---|---|---|---|
| `EMAIL_ALREADY_IN_USE` | 409 Conflict | auth-service | Registration attempted with an email that already has an account |
| `INVALID_CREDENTIALS` | 401 Unauthorized | auth-service | Login attempted with wrong email or password |
| `USER_SERVICE_UNAVAILABLE` | 503 Service Unavailable | auth-service | auth-service could not reach user-service during registration |
| `USER_PROFILE_NOT_FOUND` | 404 Not Found | user-service | Requested profile UUID does not exist in the database |
| `USER_PROFILE_ALREADY_EXISTS` | 409 Conflict | user-service | Internal creation attempted for a UUID that already has a profile |
| `UNTRUSTED_GATEWAY` | 401 Unauthorized | user-service | Request to /users/* did not carry the correct X-Gateway-Key header |
| `UNAUTHORIZED_INTERNAL_REQUEST` | 401 Unauthorized | user-service | Request to /internal/* did not carry the correct X-Internal-Api-Key header |
| `VALIDATION_ERROR` | 400 Bad Request | both services | One or more request fields failed constraint validation |

## Step 1: Create TrustedCallerFilter.java

**Path:** `backend/user-service/src/main/java/com/herafi/user_service/config/TrustedCallerFilter.java`

## Step 2: Paste this exactly

```java
package com.herafi.user_service.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TrustedCallerFilter extends OncePerRequestFilter {

    private final String gatewaySharedSecret;
    private final String internalApiKey;

    public TrustedCallerFilter(
            @Value("${service.security.gateway-shared-secret}") String gatewaySharedSecret,
            @Value("${service.security.internal-api-key}") String internalApiKey) {
        this.gatewaySharedSecret = gatewaySharedSecret;
        this.internalApiKey = internalApiKey;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.startsWith("/users/")
                && !matches(request.getHeader("X-Gateway-Key"), gatewaySharedSecret)) {
            reject(response, "UNTRUSTED_GATEWAY", "Request did not come from the API gateway");
            return;
        }

        if (path.startsWith("/internal/")
                && !matches(request.getHeader("X-Internal-Api-Key"), internalApiKey)) {
            reject(response, "UNAUTHORIZED_INTERNAL_REQUEST", "Invalid internal service key");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean matches(String actual, String expected) {
        if (actual == null) {
            return false;
        }

        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void reject(
            HttpServletResponse response,
            String code,
            String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"success":false,"error":{"code":"%s","message":"%s","errors":[]}}
                """.formatted(code, message).trim());
    }
}
```

### Reading the code

The filter runs before any controller. It checks the request path:

- Paths starting with `/users/` must carry `X-Gateway-Key` matching the configured gateway secret. If not, the filter calls `reject()` and returns — the request never reaches a controller.
- Paths starting with `/internal/` must carry `X-Internal-Api-Key` matching the configured internal API key. If not, same result.
- All other paths (like `/actuator/health`) are allowed through without any key check.

The `reject()` method writes the error JSON directly to the response and sets a 401 status. Notice that `GlobalExceptionHandler` is not involved here — the filter runs outside the Spring MVC layer, so exception handlers cannot intercept it. The JSON is written manually to match the same `ApiErrorResponse` format used everywhere else.

The two secrets are injected via `@Value("${property.name}")` from `application.properties`. They are stored as fields rather than being re-read on every request.

---

# Part 31: UserProfileService

## Step 1: Create UserProfileService.java

**Path:** `backend/user-service/src/main/java/com/herafi/user_service/service/UserProfileService.java`

## Step 2: Paste this exactly

```java
package com.herafi.user_service.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.user_service.dto.CreateUserProfileRequest;
import com.herafi.user_service.dto.UpdateUserProfileRequest;
import com.herafi.user_service.entity.UserProfile;
import com.herafi.user_service.exception.ProfileEmailAlreadyInUseException;
import com.herafi.user_service.exception.UserProfileAlreadyExistsException;
import com.herafi.user_service.exception.UserProfileNotFoundException;
import com.herafi.user_service.repository.UserProfileRepository;

@Service
public class UserProfileService {

    private final UserProfileRepository repository;

    public UserProfileService(UserProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UserProfile create(CreateUserProfileRequest request) {
        if (repository.existsById(request.id())) {
            throw new UserProfileAlreadyExistsException();
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByEmail(email)) {
            throw new ProfileEmailAlreadyInUseException();
        }

        UserProfile profile = new UserProfile();
        profile.setId(request.id());
        profile.setEmail(email);
        profile.setFirstName(request.firstName().trim());
        profile.setLastName(request.lastName().trim());

        return repository.save(profile);
    }

    @Transactional(readOnly = true)
    public UserProfile get(UUID userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));
    }

    @Transactional
    public UserProfile update(UUID userId, UpdateUserProfileRequest request) {
        UserProfile profile = get(userId);
        profile.setFirstName(request.firstName().trim());
        profile.setLastName(request.lastName().trim());

        return repository.save(profile);
    }
}
```

### Reading the code

**create:** Performs two explicit duplicate checks before inserting. First it checks whether a profile with this UUID already exists (guarding against duplicate internal calls). Then it checks whether the email is already taken. Both checks happen inside a `@Transactional` method, which means they share a database connection with the subsequent `save()` — reducing the race condition window, though not eliminating it entirely. The database unique constraint on `email` is the final safety net.

Email normalization happens here rather than in the DTO or the controller: the email is trimmed and lowercased before storage. Using `Locale.ROOT` for lowercase conversion ensures the operation is locale-independent (some locales, like Turkish, have locale-sensitive uppercasing rules for letters like `I`).

**get:** Uses `orElseThrow` with a supplier lambda so the exception is only constructed if it is actually thrown. The UUID is passed into the exception constructor so the error message includes it, making debugging faster.

**update:** Calls `get()` internally, which already throws `UserProfileNotFoundException` if the profile does not exist. Whitespace is trimmed on name fields before saving to prevent invisible whitespace from being stored.

`@Transactional(readOnly = true)` on `get` is an optimization hint: the database driver and connection pool know no writes will occur, which allows them to skip certain overhead (like reserving undo log space in PostgreSQL).

---

# Part 32: UserProfileController

## Concepts Before the Code

### What @GetMapping and @PutMapping Do

`@GetMapping("/me")` is shorthand for `@RequestMapping(value = "/me", method = RequestMethod.GET)`. It binds the method to HTTP GET requests at that path. GET requests are for reading data; they should have no side effects.

`@PutMapping("/me")` binds to HTTP PUT. PUT is for updating an existing resource. Combined with the class-level `@RequestMapping("/users")`, the full paths are `GET /users/me` and `PUT /users/me`.

### What @RequestHeader Does

`@RequestHeader("X-User-Id")` reads a named HTTP request header and binds its value to the method parameter. Spring converts it to the declared type automatically — here, to `UUID`. If the header is absent, Spring throws `MissingRequestHeaderException`, which the `GlobalExceptionHandler` catches and converts to a 401 Unauthorized response.

### Why @RequestHeader Instead of Parsing the JWT?

The gateway verifies the JWT and sets `X-User-Id` to the user's UUID before forwarding the request to `user-service`. The `TrustedCallerFilter` has already confirmed that this request came from the gateway (by checking `X-Gateway-Key`). So by the time `UserProfileController` runs, the system has already guaranteed two things:

1. The request came from the trusted gateway
2. The gateway set `X-User-Id` only after verifying a valid JWT

Reading `@RequestHeader("X-User-Id")` is therefore safe. `user-service` does not need a JWT library, does not need the JWT secret, and cannot be exploited by a crafted token even if somehow reached directly — because `TrustedCallerFilter` would block any request without the valid gateway key.

## Step 1: Create UserProfileController.java

**Path:** `backend/user-service/src/main/java/com/herafi/user_service/controller/UserProfileController.java`

## Step 2: Paste this exactly

```java
package com.herafi.user_service.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.user_service.dto.ApiResponse;
import com.herafi.user_service.dto.UpdateUserProfileRequest;
import com.herafi.user_service.dto.UserProfileResponse;
import com.herafi.user_service.entity.UserProfile;
import com.herafi.user_service.service.UserProfileService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getCurrentUser(
            @RequestHeader("X-User-Id") UUID userId) {
        UserProfile profile = userProfileService.get(userId);
        return ApiResponse.success(UserProfileResponse.from(profile));
    }

    @PutMapping("/me")
    public ApiResponse<UserProfileResponse> updateCurrentUser(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        UserProfile profile = userProfileService.update(userId, request);
        return ApiResponse.success(UserProfileResponse.from(profile));
    }
}
```

### Reading the code

Both methods follow the same pattern:
1. Read the authenticated user's UUID from `X-User-Id`
2. Call the service with that UUID
3. Convert the returned entity to a response DTO
4. Wrap it in `ApiResponse.success()` and return

There are no if-statements. If the profile does not exist, `userProfileService.get()` throws `UserProfileNotFoundException`, which `GlobalExceptionHandler` converts to a 404 response. The controller does not need to know that — it stays focused on the happy path.

Notice that both methods return `ApiResponse<UserProfileResponse>` directly rather than `ResponseEntity<...>`. Both operations return 200 OK on success, so there is no need to override the status code.

---

# Part 33: InternalUserController

## What "Internal" Means in This Architecture

`InternalUserController` handles requests that come from other services within the system — specifically, from `auth-service` calling `user-service` during registration. These requests never come from the public internet. They arrive over the internal Docker network and must carry `X-Internal-Api-Key` in the header. `TrustedCallerFilter` enforces this.

The endpoint path is `/internal/users` rather than `/users`, so the filter knows which security rule to apply.

## Step 1: Create InternalUserController.java

**Path:** `backend/user-service/src/main/java/com/herafi/user_service/controller/InternalUserController.java`

## Step 2: Paste this exactly

```java
package com.herafi.user_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.user_service.dto.ApiResponse;
import com.herafi.user_service.dto.CreateUserProfileRequest;
import com.herafi.user_service.dto.UserProfileResponse;
import com.herafi.user_service.entity.UserProfile;
import com.herafi.user_service.service.UserProfileService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserProfileService userProfileService;

    public InternalUserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> create(
            @Valid @RequestBody CreateUserProfileRequest request) {
        UserProfile profile = userProfileService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(UserProfileResponse.from(profile)));
    }
}
```

### Reading the code

This endpoint returns `ResponseEntity<ApiResponse<UserProfileResponse>>` — a 201 Created response wrapping the profile data inside `ApiResponse`. The 201 status is important: it communicates to the caller (`auth-service`) that a new resource was created, not merely that the request succeeded.

`@Valid @RequestBody CreateUserProfileRequest request` runs the constraint annotations on the DTO before the method body executes. This means if `auth-service` sends a malformed request (missing UUID, invalid email, blank name), the response will be 400 Bad Request with a structured `VALIDATION_ERROR` body rather than a confusing exception.

The controller itself has zero logic. All business rules live in `UserProfileService.create()`.

---

# Part 34: Exception Classes for user-service

## Step 1: Create UserProfileAlreadyExistsException.java

**Path:** `backend/user-service/src/main/java/com/herafi/user_service/exception/UserProfileAlreadyExistsException.java`

## Step 2: Paste this exactly

```java
package com.herafi.user_service.exception;

public class UserProfileAlreadyExistsException extends RuntimeException {

    public UserProfileAlreadyExistsException() {
        super("User profile already exists");
    }
}
```

## Step 3: Create UserProfileNotFoundException.java

**Path:** `backend/user-service/src/main/java/com/herafi/user_service/exception/UserProfileNotFoundException.java`

## Step 4: Paste this exactly

```java
package com.herafi.user_service.exception;

import java.util.UUID;

public class UserProfileNotFoundException extends RuntimeException {

    public UserProfileNotFoundException(UUID userId) {
        super("User profile not found: " + userId);
    }
}
```

## Step 5: Create ProfileEmailAlreadyInUseException.java

**Path:** `backend/user-service/src/main/java/com/herafi/user_service/exception/ProfileEmailAlreadyInUseException.java`

## Step 6: Paste this exactly

```java
package com.herafi.user_service.exception;

public class ProfileEmailAlreadyInUseException extends RuntimeException {

    public ProfileEmailAlreadyInUseException() {
        super("Email already belongs to another profile");
    }
}
```

### Reading the code

`UserProfileNotFoundException` accepts the `UUID` in the constructor and embeds it in the message. This makes the error message immediately useful for debugging — you know exactly which profile was not found without having to cross-reference logs.

`UserProfileAlreadyExistsException` and `ProfileEmailAlreadyInUseException` take no parameters. The messages are static because the context (which profile, which email) is already known to the service layer calling the exception, and for security reasons we do not want to echo user-supplied data back in error messages without careful thought.

---

# Part 35: GlobalExceptionHandler for user-service

## Step 1: Create GlobalExceptionHandler.java

**Path:** `backend/user-service/src/main/java/com/herafi/user_service/exception/GlobalExceptionHandler.java`

## Step 2: Paste this exactly

```java
package com.herafi.user_service.exception;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.herafi.user_service.dto.ApiErrorResponse;
import com.herafi.user_service.dto.ApiErrorResponse.FieldValidationError;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserProfileNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(UserProfileNotFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("USER_PROFILE_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(UserProfileAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleAlreadyExists(
            UserProfileAlreadyExistsException exception) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("USER_PROFILE_ALREADY_EXISTS", exception.getMessage()));
    }

    @ExceptionHandler({ProfileEmailAlreadyInUseException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiErrorResponse> handleEmailConflict(Exception exception) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(
                        "PROFILE_EMAIL_ALREADY_IN_USE",
                        "Email already belongs to another profile"
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception) {
        List<FieldValidationError> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldValidationError(
                        error.getField(),
                        error.getDefaultMessage()
                ))
                .toList();

        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.validation(errors));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingIdentity(
            MissingRequestHeaderException exception) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of(
                        "MISSING_USER_IDENTITY",
                        "Authenticated user identity is missing"
                ));
    }
}
```

### Reading the code

This handler is structurally the same as the auth-service handler, but with different exception types reflecting the different domain.

**handleEmailConflict** catches both the application-level `ProfileEmailAlreadyInUseException` and Spring's `DataIntegrityViolationException` for the same defensive reason described in Part 22: the database unique constraint is the last line of defence, and any violation at that level should produce the same clean error response rather than an unhandled 500.

**handleMissingIdentity** handles `MissingRequestHeaderException`, which Spring throws when a method parameter annotated with `@RequestHeader` is absent from the incoming request. In a correctly configured system this should never happen (the gateway always injects `X-User-Id`), but if it does — perhaps during development or due to a misconfigured gateway — the response is a clear 401 Unauthorized rather than a confusing 400 Bad Request with a Spring-generated message.

---

# Part 36: application.properties for user-service

## Step 1: Create application.properties

**Path:** `backend/user-service/src/main/resources/application.properties`

## Step 2: Paste this exactly

```properties
spring.application.name=user-service
server.port=${SERVER_PORT:8082}

spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/user_db}
spring.datasource.username=${DB_USERNAME:herafi}
spring.datasource.password=${DB_PASSWORD:herafi}

spring.jpa.hibernate.ddl-auto=update
spring.jpa.open-in-view=false
spring.jpa.show-sql=${SHOW_SQL:true}

service.security.gateway-shared-secret=${GATEWAY_SHARED_SECRET:7irafi-development-gateway-key}
service.security.internal-api-key=${INTERNAL_API_KEY:7irafi-development-internal-key}

management.endpoints.web.exposure.include=health
```

### Reading the config

`user-service` runs on port 8082 by default — different from `auth-service` on 8081, so both can run simultaneously on the same machine during development.

The two security properties are the keys that `TrustedCallerFilter` uses to authenticate callers:

- `service.security.gateway-shared-secret` — must match what the API gateway sends in `X-Gateway-Key`
- `service.security.internal-api-key` — must match what `auth-service` sends in `X-Internal-Api-Key`

Both have development-safe defaults. In production, these must be set as environment variables with strong, randomly generated values. They should not be shared with external parties.

There is no JWT configuration here, confirming the architecture: `user-service` has no knowledge of tokens or cryptography.

---

# Part 37: application-test.properties for user-service

## Step 1: Create application-test.properties

**Path:** `backend/user-service/src/test/resources/application-test.properties`

## Step 2: Paste this exactly

```properties
spring.datasource.url=jdbc:h2:mem:user_service_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=false

service.security.gateway-shared-secret=test-gateway-key
service.security.internal-api-key=test-internal-key
```

### Reading the config

The structure mirrors `auth-service`'s test config: H2 replaces PostgreSQL, `create-drop` ensures a clean schema for every test run, and short fixed strings replace the secrets (no need for security in tests, just configuration completeness).

The database name is `user_service_test` (distinct from `auth_service_test`) so that if both test suites ever run in the same JVM, they use separate in-memory databases.

---

# Part 38: user-service Tests

## Concepts Before the Code

### What @DataJpaTest Does (and Why We Did Not Use It Here)

`@DataJpaTest` is a slice test annotation that loads only the JPA-related parts of the application context — repositories, entities, and the database. It excludes web layers, service beans, and security filters. It is ideal for testing repository queries in isolation.

We are not using `@DataJpaTest` in these tests because we want to test the `UserProfileService`, which sits above the repository. Slice tests cannot easily wire together a service with its repository dependencies. So we use `@SpringBootTest` instead, which loads the full context including both the service and the repository.

### What @Transactional on a Test Method Does

When you annotate a test class (or method) with `@Transactional`, Spring wraps each test method in a database transaction and rolls it back after the test completes — even if the test passed. This means database changes made during one test are invisible to all other tests, and every test starts from a clean state without you having to write cleanup code.

This is one of the most useful testing patterns in Spring: you can write, read, and even save to the database during your test, and none of those changes will persist to affect the next test.

## Step 1: Create UserServiceApplicationTests.java

**Path:** `backend/user-service/src/test/java/com/herafi/user_service/UserServiceApplicationTests.java`

## Step 2: Paste this exactly

```java
package com.herafi.user_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class UserServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

## Step 3: Create UserProfileServiceTests.java

**Path:** `backend/user-service/src/test/java/com/herafi/user_service/service/UserProfileServiceTests.java`

## Step 4: Paste this exactly

```java
package com.herafi.user_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.user_service.dto.CreateUserProfileRequest;
import com.herafi.user_service.dto.UpdateUserProfileRequest;
import com.herafi.user_service.entity.UserProfile;
import com.herafi.user_service.exception.UserProfileAlreadyExistsException;
import com.herafi.user_service.exception.UserProfileNotFoundException;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class UserProfileServiceTests {

    @Autowired
    private UserProfileService service;

    @Test
    void createsNormalizedProfile() {
        UUID id = UUID.randomUUID();
        CreateUserProfileRequest request = new CreateUserProfileRequest(
                id,
                "  USER@Example.com ",
                " Youssef ",
                " Rachidi "
        );

        UserProfile profile = service.create(request);

        assertEquals(id, profile.getId());
        assertEquals("user@example.com", profile.getEmail());
        assertEquals("Youssef", profile.getFirstName());
        assertEquals("Rachidi", profile.getLastName());
    }

    @Test
    void rejectsDuplicateProfileId() {
        UUID id = UUID.randomUUID();
        CreateUserProfileRequest request = new CreateUserProfileRequest(
                id,
                "user@example.com",
                "Youssef",
                "Rachidi"
        );

        service.create(request);

        assertThrows(UserProfileAlreadyExistsException.class, () -> service.create(request));
    }

    @Test
    void updatesExistingProfile() {
        UUID id = UUID.randomUUID();
        service.create(new CreateUserProfileRequest(
                id,
                "user@example.com",
                "Old",
                "Name"
        ));

        UserProfile updated = service.update(
                id,
                new UpdateUserProfileRequest(" New ", " Name ")
        );

        assertEquals("New", updated.getFirstName());
        assertEquals("Name", updated.getLastName());
    }

    @Test
    void rejectsMissingProfile() {
        UUID id = UUID.randomUUID();

        UserProfileNotFoundException exception = assertThrows(
                UserProfileNotFoundException.class,
                () -> service.get(id)
        );

        assertTrue(exception.getMessage().contains(id.toString()));
    }
}
```

### Reading the tests

**createsNormalizedProfile:** This test deliberately passes messy input — an email with mixed case and surrounding whitespace, name fields with leading and trailing spaces. It then asserts that the stored values are normalized: email lowercased and trimmed, names trimmed. This verifies that the normalization logic in `UserProfileService.create()` actually runs and produces the right output.

**rejectsDuplicateProfileId:** Creates a profile, then tries to create it again with the same UUID. Asserts that the second call throws `UserProfileAlreadyExistsException`. Because the test class is `@Transactional`, both `create` calls are in the same transaction and the first insert is visible to the second check — confirming that the `existsById` guard works correctly.

**updatesExistingProfile:** Creates a profile with one name, updates it with whitespace-padded new values, and asserts the stored names are trimmed. Verifies both that the update took effect and that whitespace normalization applies to updates as well as creates.

**rejectsMissingProfile:** Calls `get()` with a randomly generated UUID that was never inserted. Asserts `UserProfileNotFoundException` is thrown and checks that the exception message contains the UUID string. This confirms that the error message is useful for debugging: when a profile is not found, you can see exactly which UUID was missing.

Every test uses a fresh `UUID.randomUUID()` as the profile ID. This means tests are naturally isolated — there is no risk of one test's data colliding with another's, even if the `@Transactional` rollback were somehow skipped.



# Part 39: Build the API gateway

The API gateway is the single front door for your entire system. Every request from every client — browser, mobile app, Postman — hits the gateway first. The gateway decides whether to let the request through, validates the JWT if one is required, enriches the request with identity headers, and then forwards it to the right downstream service. The downstream services never speak directly to the outside world.

This design gives you one place to enforce authentication rules, one place to handle CORS, and one place to control what headers reach your internal services. If you need to change your security policy tomorrow, you change it in the gateway, not in every service.

Before we write a single line of code, there is a decision to explain: Spring Cloud Gateway comes in two flavors.

**Spring Cloud Gateway (WebFlux)** is the older, more famous version. It is built on Project Reactor and Netty. Everything is non-blocking and reactive. You write code with `Mono<>` and `Flux<>`. It is powerful, but if you are not already comfortable with reactive programming, the debugging experience can feel like reading a stack trace written in a foreign language.

**Spring Cloud Gateway Server MVC** is the newer version. It runs on top of the standard Servlet stack — the same threading model you already know from your auth-service and user-service. Requests are handled by threads, not reactive pipelines. You write `RouterFunction<ServerResponse>` beans. The code reads like normal Java. That is why we chose it. This is a course about building a working system, not about advanced reactor patterns.

---

## Step 1: Create the gateway module

The gateway lives alongside auth-service and user-service in the backend directory. Create the directory:

```
backend/
  auth-service/
  user-service/
  api-gateway/       <-- create this
    pom.xml
    Dockerfile
    src/
      main/
        java/com/herafi/api_gateway/
          ApiGatewayApplication.java
          config/
            SecurityConfig.java
            JwtConfig.java
            GatewayRoutesConfig.java
          filter/
            GatewayIdentityFilter.java
        resources/
          application.properties
      test/
        java/com/herafi/api_gateway/
          ApiGatewayApplicationTests.java
          filter/
            GatewayIdentityFilterTests.java
```

---

# Part 39 continued: pom.xml and ApiGatewayApplication.java

## What goes in the pom.xml

The gateway has three runtime dependencies:

1. **spring-boot-starter-actuator** — provides `/actuator/health`. Docker Compose and Kubernetes use this to know when the gateway is ready.

2. **spring-boot-starter-security-oauth2-resource-server** — this is the Spring Security module that makes the gateway act as an OAuth2 resource server. A resource server is any server that validates Bearer tokens rather than issuing them. The gateway receives a JWT from the client, validates it, and grants or denies access.

3. **spring-cloud-starter-gateway-server-webmvc** — the MVC-based gateway engine. This is the heart of the routing logic.

The `<dependencyManagement>` block imports the Spring Cloud BOM (Bill of Materials). A BOM is a special POM that lists compatible versions of a set of libraries. By importing it, you let Spring Cloud manage version numbers for all its components. You do not have to guess which version of the gateway is compatible with which version of Spring Boot.

**Step 1: Create the file at `backend/api-gateway/pom.xml`**

**Step 2: Paste this exactly:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>

    <groupId>com.herafi</groupId>
    <artifactId>api-gateway</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>api-gateway</name>
    <description>API gateway for 7irafi</description>

    <properties>
        <java.version>21</java.version>
        <spring-cloud.version>2025.1.2</spring-cloud.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway-server-webmvc</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

## ApiGatewayApplication.java

This is the entry point. There is nothing unusual here. The `@SpringBootApplication` annotation tells Spring Boot to scan the package for components, auto-configure everything it finds, and start the embedded Tomcat server. The gateway uses Tomcat just like your other services because we are on the MVC stack, not Netty.

**Step 1: Create the file at `backend/api-gateway/src/main/java/com/herafi/api_gateway/ApiGatewayApplication.java`**

**Step 2: Paste this exactly:**

```java
package com.herafi.api_gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

---

# Part 40: SecurityConfig for the api-gateway

## What a resource server is

When an auth-server issues a JWT, every other server in the system that needs to verify that token is called a **resource server**. The name comes from OAuth2 terminology: the auth server is the authorization server, the protected API is the resource server, and the caller is the client.

In our system:
- The auth-service is the authorization server. It signs tokens.
- The api-gateway is the resource server. It validates tokens.
- The user-service never sees tokens. It only sees the enriched headers the gateway sends.

The line `.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))` wires all of this together. It tells Spring Security to intercept every request, look for an `Authorization: Bearer <token>` header, decode the JWT, validate the signature, check expiry, and set the authenticated principal in the security context. If validation fails, Spring returns a 401 before your code ever runs.

## What "stateless" means

`.sessionCreationPolicy(SessionCreationPolicy.STATELESS)` tells Spring Security to never create an HTTP session. Traditional web apps store login state in a server-side session identified by a cookie. REST APIs and gateways must not do this. Every request carries its own proof of identity in the JWT. The server holds no state between requests. This makes the gateway horizontally scalable: any instance can handle any request because no session data lives on disk.

## What CORS is and why the gateway handles it

**CORS (Cross-Origin Resource Sharing)** is a browser security mechanism. Browsers block JavaScript from making requests to a different origin than the page was loaded from. "Origin" means scheme + host + port. Your Angular app runs on `http://localhost:4200`. Your API runs on `http://localhost:8080`. Different ports — different origins. Without CORS headers, the browser refuses the request.

The server opts in to cross-origin requests by including response headers like `Access-Control-Allow-Origin`. The browser checks these headers on a preflight OPTIONS request before sending the real request.

**Why does the gateway handle CORS instead of each service?**

Because the browser talks only to the gateway. The browser never makes a direct request to auth-service or user-service. Those services run on internal Docker ports with no public exposure in production. If you configured CORS on each service, it would never be applied — the browser requests never reach them. The gateway is the only public boundary, so CORS configuration lives there.

## What the security rules mean

```
.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
```
This allows all preflight OPTIONS requests through without authentication. If you block OPTIONS, CORS breaks because the browser cannot complete its preflight check.

```
.requestMatchers("/auth/**", "/actuator/health").permitAll()
```
These paths need no JWT. Registration, login, and the health check are public.

```
.requestMatchers("/users/**").authenticated()
```
Every request to `/users/**` must carry a valid JWT. Spring Security verifies this before the request reaches your routing logic.

```
.anyRequest().denyAll()
```
This is the most important line. Anything not explicitly allowed is denied. This is the correct default posture. You add permissions by exception, not by blanket allowing.

**Step 1: Create the file at `backend/api-gateway/src/main/java/com/herafi/api_gateway/config/SecurityConfig.java`**

**Step 2: Paste this exactly:**

```java
package com.herafi.api_gateway.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/auth/**", "/actuator/health").permitAll()
                        .requestMatchers("/users/**").authenticated()
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${gateway.cors.allowed-origin}") String allowedOrigin) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

---

# Part 41: JwtConfig for the api-gateway

## How the gateway verifies JWT signatures without calling auth-service

When auth-service creates a JWT, it signs it using HMAC-SHA256 with a secret key. The signature is part of the token. Anyone who knows the same secret key can verify the signature without contacting auth-service.

This is the key architectural insight: JWT validation is **local and stateless**. The gateway does not need to call auth-service every time it receives a request. It just recomputes the HMAC and compares it to the signature in the token. If they match and the token has not expired, it is valid.

**Why does the gateway need the same JWT_SECRET as auth-service?**

Because HMAC is symmetric. The same key is used to sign and to verify. Auth-service signs with `JWT_SECRET`. The gateway verifies with `JWT_SECRET`. If the keys are different, every token will appear invalid. Both services read the secret from an environment variable, and Docker Compose sets them to the same value. In production, both services would read the secret from a secrets manager like AWS Secrets Manager or HashiCorp Vault.

## What NimbusJwtDecoder does

Nimbus JOSE+JWT is the library that Spring Security uses under the hood to decode and verify JWTs. `NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build()` creates a decoder that:

1. Base64-decodes the JWT's three parts (header, payload, signature).
2. Recomputes the HMAC-SHA256 signature using your secret key.
3. Compares the computed signature to the one in the token.
4. Checks that the token has not expired (`exp` claim).
5. Optionally validates the issuer (`iss` claim) if you configure a validator.

The line `decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer))` adds issuer validation. Tokens issued by anyone other than `7irafi-auth-service` will be rejected even if the signature is technically valid. This defends against a scenario where someone uses a different server with the same secret key to issue tokens.

## The 32-byte minimum

The code checks `if (secretBytes.length < 32)` and throws an error if the secret is too short. This is a security requirement. HMAC-SHA256 uses a 256-bit (32-byte) key. If your secret is shorter, the effective key space shrinks, making brute-force attacks easier. The check happens at startup so you catch misconfiguration immediately rather than discovering it after deploying to production.

**Step 1: Create the file at `backend/api-gateway/src/main/java/com/herafi/api_gateway/config/JwtConfig.java`**

**Step 2: Paste this exactly:**

```java
package com.herafi.api_gateway.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtConfig {

    @Bean
    SecretKey jwtSecretKey(@Value("${security.jwt.secret}") String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT secret must contain at least 32 bytes");
        }

        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(
            SecretKey secretKey,
            @Value("${security.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));

        return decoder;
    }
}
```

---

# Part 42: GatewayRoutesConfig

## What RouterFunction is

`RouterFunction<ServerResponse>` is Spring MVC's functional-style routing API. Instead of controllers and `@RequestMapping` annotations, you define routes as lambda expressions. A `RouterFunction` maps a predicate (does this request match?) to a handler (what to do with it?).

Spring Cloud Gateway Server MVC builds on this API. When you call `route("auth-service")`, you get a builder that produces a `RouterFunction`. The route definition reads almost like English:

- `.route(path("/auth/**"), http())` — if the path matches `/auth/**`, forward it via HTTP.
- `.before(removeInternalHeaders())` — before forwarding, run this filter to strip dangerous headers.
- `.before(uri(authServiceUrl))` — before forwarding, set the destination URL.

The order of `.before()` calls matters. They execute top to bottom. You strip headers first, then set the URI.

## Two routes, two different behaviors

**Route 1: auth-service**

The auth route handles `/auth/**`. These are unauthenticated endpoints — login, registration, password reset. The only filter applied here is `removeInternalHeaders()`. We strip any identity headers the client might have forged, but we do not add any, because there is no authenticated user yet.

**Route 2: user-service**

The user route handles `/users/**`. These are authenticated endpoints. The filter is `addAuthenticatedIdentity(gatewaySharedSecret)`. This filter reads the validated JWT from Spring Security's context (it was already validated by the SecurityFilterChain), extracts the user's ID, email, and role, and adds them as trusted headers. It also adds the `X-Gateway-Key` header so user-service can verify the request came from a legitimate gateway instance.

**Why does the gateway not expose /internal/** routes?**

There is no route registered for `/internal/**`. Because `SecurityConfig` denies all unmatched requests with `.anyRequest().denyAll()`, any client that sends a request to `/internal/...` receives a 403 immediately at the gateway level. The internal endpoints of downstream services are never reachable from outside the Docker network. This is defence in depth: the gateway routing configuration enforces it, and the Docker network isolation enforces it again.

**Step 1: Create the file at `backend/api-gateway/src/main/java/com/herafi/api_gateway/config/GatewayRoutesConfig.java`**

**Step 2: Paste this exactly:**

```java
package com.herafi.api_gateway.config;

import static com.herafi.api_gateway.filter.GatewayIdentityFilter.addAuthenticatedIdentity;
import static com.herafi.api_gateway.filter.GatewayIdentityFilter.removeInternalHeaders;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    RouterFunction<ServerResponse> authServiceRoute(
            @Value("${services.auth-service.url}") String authServiceUrl) {
        return route("auth-service")
                .route(path("/auth/**"), http())
                .before(removeInternalHeaders())
                .before(uri(authServiceUrl))
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> userServiceRoute(
            @Value("${services.user-service.url}") String userServiceUrl,
            @Value("${gateway.shared-secret}") String gatewaySharedSecret) {
        return route("user-service")
                .route(path("/users/**"), http())
                .before(addAuthenticatedIdentity(gatewaySharedSecret))
                .before(uri(userServiceUrl))
                .build();
    }
}
```

---

# Part 43: GatewayIdentityFilter

## The problem this filter solves

Without this filter, a malicious client could send a request like this:

```
GET /users/me HTTP/1.1
Authorization: Bearer <valid-token-for-user-A>
X-User-Id: <id-of-user-B>
X-User-Email: admin@company.com
X-User-Role: ADMIN
```

If user-service naively trusted the `X-User-Id` header from the request, the attacker would impersonate anyone. The GatewayIdentityFilter closes this attack vector by:

1. Stripping all identity headers from the incoming request.
2. Adding fresh identity headers derived from the verified JWT.

The JWT has already been validated by Spring Security before the filter runs. There is no way to forge a valid JWT without knowing the secret key.

## Why does the gateway strip incoming X-User-Id headers before adding its own?

Because the client controls the incoming request headers. Any header a client sends is untrusted. The gateway's job is to replace untrusted headers with verified information derived from the JWT signature. If the gateway only added headers without first removing them, a client who happens to include a forged `X-User-Id` alongside a valid token for a different user could confuse downstream services that process headers naively.

Always strip first. Then add your verified values. This way the downstream service sees exactly one value for each header, and that value came from the gateway.

## How ServerRequest.from() works

`ServerRequest` is immutable. You cannot modify a request in place. Instead, `ServerRequest.from(request)` creates a builder pre-populated with all the existing request properties. You then call `.headers(consumer -> ...)` to provide a lambda that mutates the headers on the builder. Finally you call `.build()` to produce a new immutable `ServerRequest` with your changes applied.

This pattern — copy, mutate on the builder, build — is common in functional and immutable programming. It ensures that no code holds a reference to a half-modified request.

## The five headers

| Header | Source | Purpose |
|---|---|---|
| `X-User-Id` | JWT `sub` claim | The user's UUID primary key |
| `X-User-Email` | JWT `email` claim | The user's email address |
| `X-User-Role` | JWT `role` claim | The user's role (USER, ADMIN) |
| `X-Gateway-Key` | `gateway.shared-secret` property | Proves the request came from a legitimate gateway |
| `X-Internal-Api-Key` | Stripped, never forwarded | Used only for auth-service to user-service communication |

User-service checks `X-Gateway-Key` on every request. If the value does not match the configured secret, user-service rejects the request with a 403. This ensures that even if someone on the internal Docker network tries to call user-service directly, they cannot impersonate a gateway request without knowing the shared secret.

**Step 1: Create the file at `backend/api-gateway/src/main/java/com/herafi/api_gateway/filter/GatewayIdentityFilter.java`**

**Step 2: Paste this exactly:**

```java
package com.herafi.api_gateway.filter;

import java.util.function.Function;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.function.ServerRequest;

public final class GatewayIdentityFilter {

    private static final String USER_ID = "X-User-Id";
    private static final String USER_EMAIL = "X-User-Email";
    private static final String USER_ROLE = "X-User-Role";
    private static final String GATEWAY_KEY = "X-Gateway-Key";
    private static final String INTERNAL_API_KEY = "X-Internal-Api-Key";

    private GatewayIdentityFilter() {
    }

    public static Function<ServerRequest, ServerRequest> removeInternalHeaders() {
        return request -> ServerRequest.from(request)
                .headers(GatewayIdentityFilter::removeTrustedHeaders)
                .build();
    }

    public static Function<ServerRequest, ServerRequest> addAuthenticatedIdentity(
            String gatewaySharedSecret) {
        return request -> {
            Authentication authentication = SecurityContextHolder
                    .getContext()
                    .getAuthentication();

            if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
                throw new IllegalStateException("Authenticated JWT is missing");
            }

            String email = jwtAuthentication.getToken().getClaimAsString("email");
            String role = jwtAuthentication.getToken().getClaimAsString("role");

            return ServerRequest.from(request)
                    .headers(headers -> {
                        removeTrustedHeaders(headers);
                        headers.set(USER_ID, jwtAuthentication.getToken().getSubject());
                        headers.set(USER_EMAIL, email);
                        headers.set(USER_ROLE, role);
                        headers.set(GATEWAY_KEY, gatewaySharedSecret);
                    })
                    .build();
        };
    }

    private static void removeTrustedHeaders(HttpHeaders headers) {
        headers.remove(USER_ID);
        headers.remove(USER_EMAIL);
        headers.remove(USER_ROLE);
        headers.remove(GATEWAY_KEY);
        headers.remove(INTERNAL_API_KEY);
    }
}
```

---

# Part 44: application.properties for api-gateway

## How environment variables override properties

Every value in this file follows the pattern `${ENV_VAR_NAME:default-value}`. The part before the colon is the environment variable name. The part after is the fallback used when the variable is not set.

This gives you two modes:

- **Local development without Docker:** Run the gateway directly from your IDE. It falls back to `localhost:8081` and `localhost:8082` for service URLs, which is where your locally-running services listen.
- **Docker Compose:** Docker Compose sets `AUTH_SERVICE_URL=http://auth-service:8081`. The property reads the environment variable and uses the Docker internal hostname instead.

## Why container service names work as hostnames

Inside a Docker Compose network, every service can reach every other service using its service name as the hostname. When Docker creates the network, it registers each service name in an internal DNS. So `http://auth-service:8081` resolves to auth-service's container IP within the Docker network. No manual IP management required.

This only works inside the Docker network. From your laptop, `http://auth-service:8081` would fail — your laptop is not on the Docker internal network. That is why the fallback values use `localhost`.

## What management.endpoints.web.exposure.include=health does

Spring Boot Actuator exposes operational endpoints. By default, most are hidden for security. This property says: expose the `health` endpoint and nothing else. The health endpoint returns `{"status":"UP"}` when the service is running correctly. Docker Compose uses this for health checks.

**Step 1: Create the file at `backend/api-gateway/src/main/resources/application.properties`**

**Step 2: Paste this exactly:**

```properties
spring.application.name=api-gateway
server.port=${SERVER_PORT:8080}

services.auth-service.url=${AUTH_SERVICE_URL:http://localhost:8081}
services.user-service.url=${USER_SERVICE_URL:http://localhost:8082}

security.jwt.secret=${JWT_SECRET:7irafi-development-jwt-secret-change-me-2026}
security.jwt.issuer=${JWT_ISSUER:7irafi-auth-service}
gateway.shared-secret=${GATEWAY_SHARED_SECRET:7irafi-development-gateway-key}
gateway.cors.allowed-origin=${FRONTEND_ORIGIN:http://localhost:4200}

management.endpoints.web.exposure.include=health
```

---

# Part 45: api-gateway tests

## ApiGatewayApplicationTests.java

This is a context load test. `@SpringBootTest` starts the entire Spring application context. If the context fails to start — missing bean, bad configuration, failed validation — this test fails. It is a fast and effective way to catch wiring errors before they reach deployment.

The `properties` array inside `@SpringBootTest` overrides specific properties for the test environment. This is necessary because the gateway requires a JWT secret of at least 32 bytes, and the test must provide one. It also prevents the test from accidentally using production-like URLs.

Notice that the test does not need a running auth-service or user-service. The gateway does not connect to them at startup — it only contacts them when it forwards a real request. So the context loads successfully with fake URLs.

**Step 1: Create the file at `backend/api-gateway/src/test/java/com/herafi/api_gateway/ApiGatewayApplicationTests.java`**

**Step 2: Paste this exactly:**

```java
package com.herafi.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "security.jwt.secret=7irafi-test-jwt-secret-that-is-at-least-32-bytes",
        "security.jwt.issuer=7irafi-auth-service-test",
        "gateway.shared-secret=test-gateway-key",
        "gateway.cors.allowed-origin=http://localhost:4200",
        "services.auth-service.url=http://localhost:8081",
        "services.user-service.url=http://localhost:8082"
})
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

## GatewayIdentityFilterTests.java

These tests verify the most security-critical code in the entire system: header stripping and header injection. They test three cases:

1. **removesTrustedHeadersFromPublicAuthRequests** — Build a request that has forged identity headers. Apply `removeInternalHeaders()`. Verify that all five trusted headers are gone. Verify that non-sensitive headers (like `X-Request-Id`) are preserved. The last assertion matters: we strip only what we know is dangerous. We do not strip everything.

2. **replacesForgedHeadersWithAuthenticatedJwtClaims** — Build a Jwt object manually using the builder API (no HTTP call needed). Set it as the authenticated principal in the thread-local SecurityContext. Apply `addAuthenticatedIdentity()`. Verify that the headers in the output reflect the JWT claims, not the forged values from the input.

3. **rejectsIdentityInjectionWithoutAuthenticatedJwt** — Do not set any authentication in the security context. Call `addAuthenticatedIdentity()`. Verify that it throws `IllegalStateException`. This tests the defense-in-depth guard: the filter should never reach production without a valid JWT, but if it somehow does, it fails loudly rather than silently passing empty identity headers.

The `@AfterEach` method clears the SecurityContext after each test. `SecurityContextHolder` is thread-local. If you do not clear it, a test that sets a JWT will leak that context into the next test, causing false passes.

**Step 1: Create the file at `backend/api-gateway/src/test/java/com/herafi/api_gateway/filter/GatewayIdentityFilterTests.java`**

**Step 2: Paste this exactly:**

```java
package com.herafi.api_gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.function.ServerRequest;

class GatewayIdentityFilterTests {

    private static final String GATEWAY_SECRET = "test-gateway-secret";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void removesTrustedHeadersFromPublicAuthRequests() {
        ServerRequest request = requestWithForgedHeaders();

        ServerRequest filtered = GatewayIdentityFilter.removeInternalHeaders().apply(request);

        assertThat(filtered.headers().firstHeader("X-User-Id")).isNull();
        assertThat(filtered.headers().firstHeader("X-User-Email")).isNull();
        assertThat(filtered.headers().firstHeader("X-User-Role")).isNull();
        assertThat(filtered.headers().firstHeader("X-Gateway-Key")).isNull();
        assertThat(filtered.headers().firstHeader("X-Internal-Api-Key")).isNull();
        assertThat(filtered.headers().firstHeader("X-Request-Id")).isEqualTo("request-123");
    }

    @Test
    void replacesForgedHeadersWithAuthenticatedJwtClaims() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("9d5036ab-2cd6-4eb6-aa54-bc29e6d03aa4")
                .claim("email", "real@example.com")
                .claim("role", "USER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt));

        ServerRequest filtered = GatewayIdentityFilter
                .addAuthenticatedIdentity(GATEWAY_SECRET)
                .apply(requestWithForgedHeaders());

        assertThat(filtered.headers().firstHeader("X-User-Id"))
                .isEqualTo("9d5036ab-2cd6-4eb6-aa54-bc29e6d03aa4");
        assertThat(filtered.headers().firstHeader("X-User-Email"))
                .isEqualTo("real@example.com");
        assertThat(filtered.headers().firstHeader("X-User-Role")).isEqualTo("USER");
        assertThat(filtered.headers().firstHeader("X-Gateway-Key"))
                .isEqualTo(GATEWAY_SECRET);
        assertThat(filtered.headers().firstHeader("X-Internal-Api-Key")).isNull();
    }

    @Test
    void rejectsIdentityInjectionWithoutAuthenticatedJwt() {
        assertThatThrownBy(() -> GatewayIdentityFilter
                .addAuthenticatedIdentity(GATEWAY_SECRET)
                .apply(requestWithForgedHeaders()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authenticated JWT is missing");
    }

    private ServerRequest requestWithForgedHeaders() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/users/me");
        servletRequest.addHeader("X-User-Id", "forged-id");
        servletRequest.addHeader("X-User-Email", "attacker@example.com");
        servletRequest.addHeader("X-User-Role", "ADMIN");
        servletRequest.addHeader("X-Gateway-Key", "forged-gateway-key");
        servletRequest.addHeader("X-Internal-Api-Key", "forged-internal-key");
        servletRequest.addHeader("X-Request-Id", "request-123");
        return ServerRequest.create(servletRequest, List.of());
    }
}
```

---

# Part 46: Dockerfile for auth-service

## What a Dockerfile is

A Dockerfile is a recipe for building a Docker image. Each instruction adds a layer to the image. When you run `docker build`, Docker executes each instruction in order and caches the result of each layer. If nothing changes in a layer, Docker reuses the cached result on the next build. This is why layer order matters enormously for build speed.

## What each instruction does

- `FROM maven:3.9.12-eclipse-temurin-21-alpine` — Start from an official Maven image that already has Maven 3.9 and Java 21 installed. The `alpine` suffix means it is based on Alpine Linux, a minimal distribution that keeps the image small.

- `WORKDIR /app` — Set the working directory inside the container. All subsequent commands run relative to this path.

- `COPY pom.xml .` — Copy only the pom.xml first, before copying source code.

- `RUN mvn -q -DskipTests dependency:go-offline` — Download all Maven dependencies and cache them in the local Maven repository inside the container. The `-q` flag suppresses most output. `-DskipTests` skips test execution. `dependency:go-offline` resolves and downloads all dependencies declared in pom.xml.

- `COPY src src` — Now copy the source code.

- `RUN mvn -q -DskipTests package` — Compile the source, run tests, and package the application into a fat JAR in the `target/` directory.

- `EXPOSE 8081` — Documents that this container listens on port 8081. This does not actually publish the port — that happens in Docker Compose with the `ports:` mapping.

- `ENTRYPOINT ["java", "-jar", "target/auth-service-0.0.1-SNAPSHOT.jar"]` — The command to run when the container starts.

## Why Dockerfiles copy pom.xml before source code

Docker layers are cached based on file content. If the content of a layer's inputs has not changed, Docker reuses the cached result.

Here is what happens with the two-step copy strategy:

- **Build 1:** Copy pom.xml, download dependencies (takes 2 minutes). Copy source, compile (takes 30 seconds).
- **Build 2, only source code changed:** Docker sees pom.xml did not change, reuses the cached dependency layer. Skips the 2-minute download. Only compiles source (30 seconds).
- **Build 2, pom.xml changed:** New dependency added. Docker downloads dependencies again.

If you copied everything at once (`COPY . .`), any change to any file — including a comment in a Java file — would invalidate the dependency cache and force a fresh download on every build.

**Step 1: Create the file at `backend/auth-service/Dockerfile`**

**Step 2: Paste this exactly:**

```dockerfile
FROM maven:3.9.12-eclipse-temurin-21-alpine

WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src src
RUN mvn -q -DskipTests package

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "target/auth-service-0.0.1-SNAPSHOT.jar"]
```

---

# Part 47: Dockerfile for user-service

Identical structure to auth-service. Only two things differ: the exposed port (8082) and the JAR name in the ENTRYPOINT.

**Step 1: Create the file at `backend/user-service/Dockerfile`**

**Step 2: Paste this exactly:**

```dockerfile
FROM maven:3.9.12-eclipse-temurin-21-alpine

WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src src
RUN mvn -q -DskipTests package

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "target/user-service-0.0.1-SNAPSHOT.jar"]
```

---

# Part 48: Dockerfile for api-gateway

Same pattern. Port 8080, gateway JAR name.

**Step 1: Create the file at `backend/api-gateway/Dockerfile`**

**Step 2: Paste this exactly:**

```dockerfile
FROM maven:3.9.12-eclipse-temurin-21-alpine

WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src src
RUN mvn -q -DskipTests package

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "target/api-gateway-0.0.1-SNAPSHOT.jar"]
```

---

# Part 49: Run the complete system

## Understanding the docker-compose.yml startup sequence

Before you run anything, read through the `docker-compose.yml` in `infra/docker/`. The startup order is controlled by `depends_on` conditions:

```
postgres       (starts first, no dependencies)
  |
  +-- user-service   (waits for postgres to be healthy)
  |
  +-- auth-service   (waits for postgres AND user-service to be healthy)
       |
       +-- api-gateway  (waits for auth-service AND user-service to be healthy)
```

## What `depends_on: condition: service_healthy` means

`depends_on` alone tells Docker Compose to start services in order. But "started" does not mean "ready". A container can be running but still initializing. Without health checks, auth-service might try to connect to PostgreSQL before PostgreSQL has finished starting, causing startup failures and crash loops.

`condition: service_healthy` makes Docker Compose wait until the dependency service passes its health check before starting the dependent service. This is a true readiness check, not just a process-started check.

## What a Docker healthcheck does

```yaml
healthcheck:
  test: ["CMD-SHELL", "wget -qO- http://localhost:8081/actuator/health | grep -q UP"]
  interval: 5s
  timeout: 5s
  retries: 20
```

Every 5 seconds, Docker runs the `test` command inside the container. If the command exits with code 0, the container is healthy. If it exits with a non-zero code, it is unhealthy. After 20 consecutive failures, Docker marks the container as unhealthy.

The `grep -q UP` part checks that the response body contains the string "UP". Spring Boot Actuator returns `{"status":"UP"}` when the application is ready. If the application is still starting, the connection is refused and `wget` exits with a non-zero code.

**Why is `depends_on: condition: service_healthy` better than `depends_on` alone?**

Without it, Docker starts containers in order but does not wait for them to be ready. Your application sees a "connection refused" error when it tries to reach the database or a dependency service on startup. With health checks and `service_healthy`, Docker waits until each service is genuinely accepting connections before starting the next one.

## Running the system

Navigate to the docker directory and run:

```bash
cd infra/docker
docker compose up --build
```

`--build` forces Docker to rebuild all images even if they were previously built. Use this after any code change. Without `--build`, Docker Compose uses cached images and your changes are not reflected.

The first build will take several minutes — Maven downloads dependencies for all three services. Subsequent builds with no `pom.xml` changes will be much faster.

## Watching the build progress

The terminal shows output from all containers interleaved. Watch for these milestones:

```
postgres-dev      | database system is ready to accept connections
user-service-dev  | Started UserServiceApplication in 4.2 seconds
auth-service-dev  | Started AuthServiceApplication in 5.1 seconds
api-gateway-dev   | Started ApiGatewayApplication in 3.8 seconds
```

## Checking container status

In a separate terminal:

```bash
docker compose ps
```

All four services should show `healthy` in the STATUS column:

```
NAME                SERVICE       STATUS          PORTS
api-gateway-dev     api-gateway   running (healthy)   0.0.0.0:8080->8080/tcp
auth-service-dev    auth-service  running (healthy)   0.0.0.0:8081->8081/tcp
postgres-dev        postgres      running (healthy)   0.0.0.0:5432:5432/tcp
user-service-dev    user-service  running (healthy)   0.0.0.0:8082->8082/tcp
```

## Reading logs from a specific service

```bash
docker compose logs auth-service --follow
docker compose logs api-gateway --follow
```

`--follow` streams new log lines as they appear. Press Ctrl+C to stop following.

## Stopping the system

```bash
docker compose down
```

This stops and removes containers but preserves volumes. Your database data survives.

```bash
docker compose down -v
```

This also removes volumes. PostgreSQL data is deleted. The next `docker compose up` starts with a fresh database. Use this when you want to reset everything.

**Why does PostgreSQL only run init scripts when the volume is new?**

The `./postgres/init:/docker-entrypoint-initdb.d:ro` volume mount points PostgreSQL to your SQL init scripts. PostgreSQL only runs these scripts when the data directory is empty — that is, when the volume is brand new. If you already have data in the volume, PostgreSQL ignores the init scripts entirely on subsequent starts.

This is intentional behavior. Init scripts are for first-time setup. If PostgreSQL re-ran them on every startup, it would try to `CREATE DATABASE user_db` when it already exists, causing an error. If your init scripts changed and you need to re-run them, use `docker compose down -v` to delete the volume, then start fresh.

---

# Part 50: Test the complete flow

These seven tests verify that the entire system — from client request to database and back — works correctly. Run them in order. Each test builds on the state left by the previous one.

All requests go to port 8080, which is the gateway. You never call auth-service or user-service directly.

## Test 1: Register a new user

```bash
curl -s -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "youssef@example.com",
    "password": "StrongPass123!",
    "firstName": "Youssef",
    "lastName": "Rachidi"
  }' | jq .
```

**Expected response (201 Created):**

```json
{
  "id": "9d5036ab-2cd6-4eb6-aa54-bc29e6d03aa4",
  "email": "youssef@example.com",
  "firstName": "Youssef",
  "lastName": "Rachidi",
  "role": "USER"
}
```

The UUID in `id` will be different each time. This is the user's identifier in both auth_db and user_db.

## Test 2: Login and capture the token

```bash
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "youssef@example.com",
    "password": "StrongPass123!"
  }' | jq .
```

**Expected response (200 OK):**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI5ZDUwMzZhYi0..."
}
```

Copy the token value and store it in a shell variable for the remaining tests:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"youssef@example.com","password":"StrongPass123!"}' \
  | jq -r '.accessToken')

echo $TOKEN
```

You should see the full JWT printed to the terminal.

## Test 3: Get your profile with the token

```bash
curl -s -X GET http://localhost:8080/users/me \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**Expected response (200 OK):**

```json
{
  "id": "9d5036ab-2cd6-4eb6-aa54-bc29e6d03aa4",
  "email": "youssef@example.com",
  "firstName": "Youssef",
  "lastName": "Rachidi",
  "bio": null,
  "phoneNumber": null,
  "role": "USER"
}
```

What happened behind the scenes: the gateway received the request, validated the JWT, extracted the user ID, email, and role, added those as trusted headers, and forwarded the request to user-service. User-service used `X-User-Id` to look up the profile in user_db and returned it. Your code in user-service never decoded a JWT — it trusted the header that the gateway injected.

## Test 4: Update your profile with the token

```bash
curl -s -X PUT http://localhost:8080/users/me \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "bio": "Backend developer learning microservices",
    "phoneNumber": "+212600000000"
  }' | jq .
```

**Expected response (200 OK):**

```json
{
  "id": "9d5036ab-2cd6-4eb6-aa54-bc29e6d03aa4",
  "email": "youssef@example.com",
  "firstName": "Youssef",
  "lastName": "Rachidi",
  "bio": "Backend developer learning microservices",
  "phoneNumber": "+212600000000",
  "role": "USER"
}
```

## Test 5: Access a protected route without a token

```bash
curl -s -X GET http://localhost:8080/users/me | jq .
```

**Expected response (401 Unauthorized):**

```json
{
  "error": "Unauthorized",
  "path": "/users/me",
  "status": 401
}
```

Spring Security intercepted the request before it reached the routing logic. No token means no access. This is enforced at the gateway level — user-service never received this request.

## Test 6: Try to forge identity headers without a token

```bash
curl -s -X GET http://localhost:8080/users/me \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" \
  -H "X-User-Email: admin@company.com" \
  -H "X-User-Role: ADMIN" | jq .
```

**Expected response (401 Unauthorized):**

```json
{
  "error": "Unauthorized",
  "path": "/users/me",
  "status": 401
}
```

The gateway rejected the request because there was no valid JWT in the `Authorization` header. The forged identity headers were irrelevant — Spring Security checks the JWT first, before any routing or filter runs. Even if this request had somehow made it to the filter, `GatewayIdentityFilter` would have stripped the forged headers and replaced them with the JWT-derived values. There are two independent defenses in place.

## Test 7: Login with the wrong password

```bash
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "youssef@example.com",
    "password": "WrongPassword!"
  }' | jq .
```

**Expected response (401 Unauthorized):**

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Invalid email or password"
}
```

This error comes from auth-service. The gateway forwarded the request (no JWT required for `/auth/**`), auth-service checked the password against the bcrypt hash in the database, found it did not match, and returned a 401 with your structured error body.

---

# Part 51: Common mistakes and how to fix them

## Mistake 1: JWT secret is too short

**What the error looks like:**

```
java.lang.IllegalStateException: JWT secret must contain at least 32 bytes
    at com.herafi.api_gateway.config.JwtConfig.jwtSecretKey(JwtConfig.java:22)
```

The application fails to start. You see this in Docker logs for api-gateway or auth-service.

**What causes it:**

You set `JWT_SECRET` to a short string like `mysecret` or left it blank.

**How to fix it:**

Set `JWT_SECRET` to a string of at least 32 characters in your `.env` file or Docker Compose environment block. You can generate one:

```bash
openssl rand -base64 48
```

Make sure the same value is used in both auth-service and api-gateway.

---

## Mistake 2: Services cannot connect to each other inside Docker

**What the error looks like:**

```
Connection refused: http://auth-service:8081
```
or
```
UnknownHostException: auth-service
```

**What causes it:**

You are trying to reach Docker service names from outside Docker, or a service name is misspelled in Docker Compose, or the service failed to start and is not registered in the internal DNS.

**How to fix it:**

- Verify all containers are running: `docker compose ps`
- Check that service names in `AUTH_SERVICE_URL` and `USER_SERVICE_URL` exactly match the service names defined in `docker-compose.yml`
- Check logs for the failing service: `docker compose logs auth-service`
- Remember: container hostnames only resolve within the Docker network, not from your laptop

---

## Mistake 3: CORS errors in the browser

**What the error looks like:**

In your browser developer console:
```
Access to fetch at 'http://localhost:8080/auth/login' from origin 'http://localhost:4200'
has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present.
```

**What causes it:**

Either `FRONTEND_ORIGIN` is not set to `http://localhost:4200`, or you are calling a URL that the gateway does not route (any unmatched path returns a 403, which has no CORS headers), or Spring Security is blocking the OPTIONS preflight before CORS headers can be added.

**How to fix it:**

1. Verify `gateway.cors.allowed-origin` in application.properties matches your Angular dev server origin exactly, including scheme and port.
2. Verify the OPTIONS pre-flight is permitted: `.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()` must appear before the other rules in SecurityConfig.
3. Verify you are calling `/auth/...` or `/users/...`, not some other path.

---

## Mistake 4: 401 on every request even with a valid token

**What the error looks like:**

Every request returns 401 even after a successful login and correct token.

**What causes it:**

The most common cause is that `JWT_SECRET` or `JWT_ISSUER` in the gateway does not match the values used in auth-service to sign the token. The gateway decodes the token, recomputes the signature, and the computed signature does not match the one in the token.

**How to fix it:**

Compare the environment variables across services in Docker Compose:

```yaml
# auth-service
JWT_SECRET: 7irafi-development-jwt-secret-change-me-2026
JWT_ISSUER: 7irafi-auth-service

# api-gateway
JWT_SECRET: 7irafi-development-jwt-secret-change-me-2026   # must match
JWT_ISSUER: 7irafi-auth-service                             # must match
```

Both values must be byte-for-byte identical. A single extra space or different capitalization will cause all token validations to fail.

---

## Mistake 5: PostgreSQL init scripts not running

**What the error looks like:**

Auth-service or user-service starts but fails with:
```
relation "users" does not exist
```
or
```
database "user_db" does not exist
```

**What causes it:**

The PostgreSQL init scripts run only once, when the data directory is empty. If you already have a `postgres-data` volume from a previous run, the scripts are skipped.

**How to fix it:**

Stop everything and delete the volume:

```bash
docker compose down -v
docker compose up --build
```

The `-v` flag removes named volumes. PostgreSQL will initialize a fresh data directory and run your init scripts.

---

## Mistake 6: user-service rejects gateway requests with 403

**What the error looks like:**

You send a correctly authenticated request through the gateway but user-service returns 403.

**What causes it:**

`GATEWAY_SHARED_SECRET` in user-service does not match the value in api-gateway. User-service checks the `X-Gateway-Key` header against its configured secret and rejects requests with the wrong key.

**How to fix it:**

Both services must share the same secret:

```yaml
# user-service
GATEWAY_SHARED_SECRET: 7irafi-development-gateway-key

# api-gateway
GATEWAY_SHARED_SECRET: 7irafi-development-gateway-key   # must match
```

---

## Mistake 7: ddl-auto=update is causing unexpected schema changes

**What `ddl-auto=update` means:**

When set to `update`, Hibernate compares your entity classes to the database schema at startup and issues `ALTER TABLE` statements to make them match. It adds new columns and creates new tables, but it never drops columns or tables (to avoid data loss).

This is convenient for development: you add a field to a `@Entity` class and the column appears in the database automatically without writing a migration.

The risk: `update` is not suitable for production. It can behave unpredictably, especially with renamed columns (it adds the new column but leaves the old one). For production, use a migration tool like Flyway or Liquibase where every schema change is an explicit, version-controlled script.

**What the error looks like when ddl-auto causes problems:**

```
SchemaManagementException: Schema-validation: missing column [new_field]
```

This happens if you somehow have `ddl-auto=validate` (common in staging environments). The entity has a field that does not exist in the database yet.

**How to fix it:**

For development, keep `spring.jpa.hibernate.ddl-auto=update`. For staging and production, use Flyway and set `ddl-auto=none` or `ddl-auto=validate`.

---

# Part 52: What we intentionally did not build yet

This system works. It handles registration, login, JWT validation, header injection, and CORS. But it is a foundation, not a finished product. Here is what comes next and why.

## Refresh tokens

The current system issues only access tokens. Access tokens in this implementation are valid for 15 minutes. When they expire, the user must log in again. In a real application, you issue a refresh token alongside the access token. The refresh token has a longer lifetime (days or weeks) and is stored in an HTTP-only cookie (not JavaScript-accessible). When the access token expires, the client sends the refresh token to `/auth/refresh` and receives a new access token silently.

Building this correctly requires a refresh token table in the database (so you can revoke them), rotation on every use (so a stolen refresh token can only be used once), and careful handling of the secure cookie.

## Redis for session and token blacklist

Right now, there is no way to log out a user. The JWT is valid until it expires. If a user's token is stolen, you cannot invalidate it without changing the JWT secret (which invalidates everyone's tokens). The standard solution is a token blacklist: a fast key-value store (Redis) that holds revoked token IDs. The gateway checks this store on every request. If the token's `jti` (JWT ID) claim is in the blacklist, the request is rejected.

Redis also helps with rate limiting: count requests per IP per minute and reject clients that exceed the threshold.

## Flyway for database migrations

`ddl-auto=update` is convenient but not reliable enough for a team or for production. Flyway gives you versioned SQL migration scripts. Every schema change is a numbered file (`V2__add_phone_number_to_users.sql`). Flyway tracks which migrations have run. The schema history is reproducible, reversible, and auditable.

## Testcontainers for integration tests

The current tests mock the database and test units in isolation. With Testcontainers, your integration tests start real PostgreSQL and Redis containers on demand, run your tests against them, and tear them down when done. Tests become more realistic and catch issues that unit tests miss — like Hibernate queries that work against H2 but fail against real PostgreSQL due to dialect differences.

## Logout endpoint

A logout endpoint should revoke the current token by adding it to the Redis blacklist and clear the refresh token cookie. This is a two-step operation that requires a database write and a cache write in the same request. Thinking about what happens if one step fails (the token is revoked but the cookie is not cleared, or vice versa) is a good exercise in failure mode analysis.

## Rate limiting

The gateway is the ideal place for rate limiting because every request passes through it. Spring Cloud Gateway Server MVC supports rate limiting with a `RequestRateLimiter` filter backed by Redis. You can limit registration attempts per IP (to prevent mass account creation bots) and login attempts per email (to prevent brute-force password attacks).

---

# Part 53: Final mental model

You have built three services and one gateway. Before you close this course, trace each flow one more time from first principles. These diagrams show every hop, every decision, and every transformation that a request goes through.

## Flow 1: Registration

```
Browser
  |
  | POST /auth/register
  | { email, password, firstName, lastName }
  |
  v
API Gateway (port 8080)
  |
  | SecurityConfig: /auth/** is permitAll(), no JWT needed
  | GatewayIdentityFilter.removeInternalHeaders()
  |   strips X-User-Id, X-User-Email, X-User-Role, X-Gateway-Key, X-Internal-Api-Key
  | Route: forward to http://auth-service:8081
  |
  v
Auth Service (port 8081)
  |
  | AuthController receives POST /auth/register
  | RegisterUseCase:
  |   1. Check email not already used (query auth_db.users)
  |   2. Hash password with BCrypt
  |   3. Save new user to auth_db.users (id, email, hashed_password, role)
  |   4. Call user-service POST /internal/users
  |        with X-Internal-Api-Key header
  |        body: { id, email, firstName, lastName, role }
  |
  v
User Service (port 8082)
  |
  | Receives POST /internal/users
  | Verifies X-Internal-Api-Key header matches INTERNAL_API_KEY env var
  | Creates UserProfile in user_db.users (same id, email, firstName, lastName)
  | Returns 201 with UserProfileResponse
  |
  ^
  |
Auth Service
  |
  | Returns 201 with UserRegistrationResponse
  | { id, email, firstName, lastName, role }
  |
  ^
  |
API Gateway
  |
  | Passes response back to browser unchanged
  |
  v
Browser
  |
  | Shows success. No token yet. User must log in.
```

## Flow 2: Login

```
Browser
  |
  | POST /auth/login
  | { email, password }
  |
  v
API Gateway (port 8080)
  |
  | SecurityConfig: /auth/** is permitAll(), no JWT needed
  | GatewayIdentityFilter.removeInternalHeaders() strips trusted headers
  | Route: forward to http://auth-service:8081
  |
  v
Auth Service (port 8081)
  |
  | AuthController receives POST /auth/login
  | LoginUseCase:
  |   1. Look up user by email in auth_db.users
  |   2. If not found: throw InvalidCredentialsException -> 401 INVALID_CREDENTIALS
  |   3. BCrypt.matches(rawPassword, storedHash)
  |   4. If no match: throw InvalidCredentialsException -> 401 INVALID_CREDENTIALS
  |   5. Build JWT:
  |        sub  = user.id (UUID)
  |        email = user.email
  |        role  = user.role
  |        iss  = JWT_ISSUER env var
  |        iat  = now
  |        exp  = now + 15 minutes
  |        Sign with HMAC-SHA256 using JWT_SECRET
  |   6. Return 200 { accessToken: "<jwt>" }
  |
  ^
  |
API Gateway
  |
  | Passes response back to browser unchanged
  |
  v
Browser
  |
  | Stores the accessToken in memory (not localStorage for security)
  | Attaches it to future requests as: Authorization: Bearer <token>
```

## Flow 3: Protected request (GET /users/me)

```
Browser
  |
  | GET /users/me
  | Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
  |
  v
API Gateway (port 8080)
  |
  | Spring Security OAuth2 Resource Server filter runs FIRST:
  |   1. Extract token from Authorization header
  |   2. Base64-decode the three JWT parts
  |   3. Recompute HMAC-SHA256(header.payload, JWT_SECRET)
  |   4. Compare computed signature to token signature
  |   5. If mismatch: return 401 immediately, request stops here
  |   6. Check exp claim: if expired, return 401
  |   7. Check iss claim: if issuer wrong, return 401
  |   8. Put JwtAuthenticationToken in Spring SecurityContext
  |
  | SecurityConfig: /users/** requires authenticated() -- check passed
  |
  | GatewayRoutesConfig: path matches /users/**, apply user-service route
  |
  | GatewayIdentityFilter.addAuthenticatedIdentity(gatewaySharedSecret):
  |   1. removeTrustedHeaders() -- strips any forged headers from client
  |   2. Read from SecurityContext: jwtAuthentication.getToken()
  |   3. Add X-User-Id    = jwt.subject     (the user's UUID)
  |   4. Add X-User-Email = jwt.email claim
  |   5. Add X-User-Role  = jwt.role claim
  |   6. Add X-Gateway-Key = gatewaySharedSecret
  |
  | Route: forward to http://user-service:8082
  |
  v
User Service (port 8082)
  |
  | Receives GET /users/me
  | GatewayAuthenticationFilter runs:
  |   1. Check X-Gateway-Key header == GATEWAY_SHARED_SECRET env var
  |   2. If wrong key: return 403 immediately
  |   3. Read X-User-Id, X-User-Email, X-User-Role
  |   4. Set GatewayAuthentication in SecurityContext
  |
  | UserController receives GET /users/me
  | @AuthenticationPrincipal gives access to the authenticated user's details
  | GetProfileUseCase:
  |   1. Look up UserProfile by userId (X-User-Id) in user_db.users
  |   2. If not found: return 404
  |   3. Return 200 with UserProfileResponse
  |
  ^
  |
API Gateway
  |
  | Passes response back to browser unchanged
  |
  v
Browser
  |
  | Displays the user's profile data
```

---

## Conclusion

You now understand how a production-grade authentication system is actually assembled. Not from a tutorial that skips the hard parts, but from real code with real security decisions at every layer.

You can explain what a JWT is and how its signature works. You can explain why the gateway validates tokens rather than each service. You can explain why header stripping must happen before header injection. You can explain why `ddl-auto=update` is convenient but dangerous in production. You can explain what a Docker healthcheck does and why `depends_on: condition: service_healthy` matters.

The system you built is not a toy. The patterns — stateless JWT validation at the boundary, trusted internal headers, shared-secret service authentication, public/internal endpoint separation — are the same patterns used in serious production systems.

The next steps are not about learning new concepts from scratch. They are about making what you built more robust: refresh tokens for better user experience, Redis for revocation and rate limiting, Flyway for reliable schema management, Testcontainers for integration tests that catch real bugs. Each of these improvements follows the same principle you already understand: make each component responsible for exactly one thing, make the boundaries explicit, and make failures loud rather than silent.

Build the next piece.
