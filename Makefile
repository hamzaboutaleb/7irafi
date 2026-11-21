COMPOSE := docker compose -f infra/docker/docker-compose.yml
PROD_COMPOSE := env -u FRONTEND_ORIGIN -u S3_PUBLIC_ENDPOINT -u TLS_CERT_SHA256 docker compose -p 7irafi-prod --env-file infra/docker/prod/.env --env-file infra/docker/prod/.env.https -f infra/docker/docker-compose.yml -f infra/docker/prod/compose.yml
BACKEND_SERVICES := auth-service user-service job-service application-service chat-service notification-service api-gateway discovery-service rating-service
SPOTLESS_SERVICES := auth-service user-service chat-service notification-service api-gateway discovery-service rating-service

.PHONY: help dev-init dev-check dev-up dev-down dev-ps dev-logs dev-observability db-init db-backup db-backups format-check test-backend test-analytics test build prod-https prod-preflight prod-build prod-up prod-ps prod-logs prod-backup prod-down prod-db-init fclean prod-fclean

help:
	@printf '%s\n' 'dev-init              Copy the environment template if absent' 'dev-check             Validate Compose configuration' 'dev-up                Start the app without optional observability' 'dev-observability     Start monitoring and logging' 'dev-down              Stop the development stack' 'dev-ps                Show service status' 'dev-logs              Follow app logs' 'db-init               Create missing service databases in an existing volume' 'db-backup             Take a database backup now' 'db-backups            List saved backups' 'format-check          Check configured Java formatters and shell syntax' 'test-backend          Run backend tests (except analytics)' 'test-analytics        Run analytics tests against a disposable Postgres' 'test                  Run all test targets' 'build                 Build application images' 'prod-https            Prepare the host IP and self-signed HTTPS certificate' 'prod-preflight        Check production environment, HTTPS assets, and Compose model' 'prod-build            Build production images' 'prod-up               Start production with HTTPS and observability' 'prod-ps               Show production service status' 'prod-logs             Follow production app logs' 'prod-db-init          Create missing databases on an existing production volume' 'prod-backup           Take a production database backup now' 'prod-down             Stop production and observability containers (keep volumes)'
	@printf '%s\n' 'fclean                Remove development stack and images; prune unused volumes across all projects (deletes data)' 'prod-fclean           Remove production containers, networks, images, and volumes (deletes data)'

dev-init:
	@test -e infra/docker/.env || cp infra/docker/.env.example infra/docker/.env
	@echo 'Set all placeholder values in infra/docker/.env before starting.'

dev-check:
	$(COMPOSE) config --quiet

dev-up: dev-check db-init
	$(COMPOSE) up --build -d frontend api-gateway chat-service notification-service analytics-service minio db-backup

dev-observability: dev-check
	$(COMPOSE) --profile observability up -d prometheus grafana node-exporter alertmanager elasticsearch es-setup logstash kibana kibana-setup

dev-down:
	$(COMPOSE) down

fclean:
	$(COMPOSE) --profile "*" down --volumes --rmi all --remove-orphans
	docker volume prune -a

dev-ps:
	$(COMPOSE) ps

dev-logs:
	$(COMPOSE) logs -f --tail=100 api-gateway auth-service user-service job-service application-service chat-service notification-service rating-service analytics-service frontend

db-init:
	$(COMPOSE) up --wait -d postgres
	$(COMPOSE) exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$$POSTGRES_USER" -d "$$POSTGRES_DB" -f /docker-entrypoint-initdb.d/01-create-databases.sql'

db-backup:
	$(COMPOSE) exec -T db-backup /scripts/backup.sh

db-backups:
	$(COMPOSE) exec -T db-backup ls -lh /backups

format-check:
	@for service in $(SPOTLESS_SERVICES); do mvn -q -f backend/$$service/pom.xml spotless:check || exit $$?; done
	@for file in infra/backup/*.sh infra/docker/kafka/*.sh infra/docker/minio/*.sh infra/docker/elasticsearch/*.sh infra/docker/kibana/*.sh infra/docker/prometheus/*.sh infra/docker/prod/*.sh; do sh -n $$file || exit $$?; done

test-backend:
	mvn -q -f backend/shared-kernel/pom.xml -DskipTests install
	@for service in $(BACKEND_SERVICES); do mvn -q -f backend/$$service/pom.xml test || exit $$?; done

test-analytics:
	@echo 'Requires a disposable PostgreSQL database; see backend/analytics-service/README.md.'
	mvn -q -f backend/analytics-service/pom.xml test

test: test-backend test-analytics

build:
	$(COMPOSE) build frontend api-gateway auth-service user-service job-service application-service chat-service notification-service rating-service analytics-service

prod-https:
	sh infra/docker/prod/prepare-https.sh

prod-preflight:
	sh infra/docker/prod/preflight.sh
	$(PROD_COMPOSE) config --quiet
	@echo 'Production configuration passed local preflight.'

prod-build: prod-preflight
	$(PROD_COMPOSE) build frontend api-gateway auth-service user-service job-service application-service chat-service notification-service rating-service analytics-service

prod-db-init: prod-preflight
	$(PROD_COMPOSE) up --wait -d postgres
	$(PROD_COMPOSE) exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$$POSTGRES_USER" -d "$$POSTGRES_DB" -f /docker-entrypoint-initdb.d/01-create-databases.sql'

prod-up: prod-https
	$(MAKE) prod-db-init
	$(PROD_COMPOSE) --profile observability up --build --wait -d
	$(PROD_COMPOSE) exec -T frontend nginx -t

prod-ps:
	$(PROD_COMPOSE) ps

prod-logs:
	$(PROD_COMPOSE) logs -f --tail=100 api-gateway auth-service user-service job-service application-service chat-service notification-service rating-service analytics-service frontend

prod-backup: prod-preflight
	$(PROD_COMPOSE) exec -T db-backup /scripts/backup.sh

prod-down:
	$(PROD_COMPOSE) --profile observability down

prod-fclean:
	$(PROD_COMPOSE) --profile "*" down --volumes --rmi all --remove-orphans
