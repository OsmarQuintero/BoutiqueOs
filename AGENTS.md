# AGENTS.md

## Project: Boutique OS

SaaS POS for a single-owner boutique (Spanish-language docs, MVP scope).

## Stack

- **Frontend:** Angular 22, standalone components, application builder (`@angular/build`), SCSS
- **Backend:** Java 21, Spring Boot 4.0.6, Spring Data JPA, H2 (file-based), Maven wrapper
- **No DB server required** — H2 file at `backend-springboot/data/boutique-os`
- **No auth** yet — CORS allows `http://localhost:4200` only

## Getting started

```sh
# Backend (terminal 1)
cd backend-springboot && ./mvnw spring-boot:run

# Frontend (terminal 2)
cd frontend-angular && npm start
```

- Backend: `http://localhost:8080`
- Frontend: `http://localhost:4200`
- H2 console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:file:./data/boutique-os`, user: `sa`, blank password)

## Architecture

### Backend (`backend-springboot/`)
- Module-per-package DDD: `product/`, `sale/`, `config/` each contain Controller, Service, Repository, Entity, DTO (records)
- Uses record DTOs (`ProductRequest`, `SaleRequest`) — never use Lombok
- All endpoints under `/api/` prefix
- Seed data in `DataSeeder.java` (runs on startup if DB empty)
- `spring.jpa.open-in-view=false`, `ddl-auto=update`

### Frontend (`frontend-angular/`)
- Single standalone `App` component with view switching (no router — routes array is empty)
- All HTTP calls point to `http://localhost:8080/api/`
- Prettier config: `singleQuote: true`, `printWidth: 100`, Angular HTML parser
- `skipTests: true` for all ng generate schematics

## Commands

| Action | Command |
|---|---|
| Run backend | `cd backend-springboot && ./mvnw spring-boot:run` |
| Run frontend | `cd frontend-angular && npm start` |
| Build frontend | `cd frontend-angular && npm run build` |
| Format code | Prettier (`singleQuote`, `printWidth 100`, Angular HTML) |

## Style & conventions

- **No Lombok** — write explicit getters/setters
- **No router** in frontend — use view switching on single component
- **DDD packages** in backend — each domain owns its Controller/Service/Repository/Entity/DTO
- All HTTP calls use full URL (`http://localhost:8080/api/...`)
- Frontend uses typed inline interfaces (no separate model files)
- Docs and code identifiers in Spanish

## Tests

- Backend: `./mvnw test` (single `@SpringBootTest` context-load test exists, no per-domain tests)
- Frontend: `npm test` → `ng test` (no test files exist; `tsconfig.spec.json` references `vitest/globals` but test runner not installed)

## No existing CI, no opencode.json, no git repo initialized
