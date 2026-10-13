# Shortcuts (Git Bash / macOS / Linux). Windows PowerShell equivalents are in README.md.
.PHONY: db api web test-backend test-frontend test-e2e build

db:            ## start PostgreSQL
	docker compose up -d db

api:           ## run the API (dev profile, loads sample recipes)
	cd backend && ./mvnw spring-boot:run

web:           ## run the frontend dev server
	cd frontend && npm run dev

test-backend:  ## unit tests + integration/security tests against real PostgreSQL
	cd backend && ./mvnw verify

test-frontend: ## lint, type-check, unit tests, production build
	cd frontend && npm run lint && npm run typecheck && npm test && npm run build

test-e2e:      ## Selenium UI tests (API + frontend must be running)
	cd e2e && ./mvnw test

build: test-backend test-frontend
