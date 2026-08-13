# AGENTS.md

## Project: Boutique OS

SaaS POS for a single-owner boutique (Spanish-language docs, MVP scope).

## Stack

- **Frontend:** Angular 22, standalone components, application builder (`@angular/build`), SCSS, Vitest (unit tests), PWA (manifest + service worker)
- **Backend:** Java 21, Spring Boot 4.0.6, Spring Data JPA, H2 (file-based), Maven wrapper
- **No DB server required** — H2 file at `backend-springboot/data/boutique-os`
- **Auth:** sesiones stateless con JWT (JJWT) + interceptor de sesion por header `X-Boutique-Session`; secret por `APP_JWT_SECRET` (sin configurar genera una clave aleatoria)
- **Multi-tenant:** `AccountContext` aislado por cuenta; `requireAccountId()` lanza 401 si no hay sesion (fail-closed)

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
- `spring.jpa.open-in-view=false`, `ddl-auto=update`
- Auth: `AuthSessionService` (JWT) + `ApiSessionInterceptor` (rutas publicas en `PUBLIC_API_PATHS`)
- Onboarding: pago Stripe -> `onboarding/` crea cuenta; `settings/` maneja login, logout, password-reset y ticket

### Frontend (`frontend-angular/`)
- Single standalone `App` component with view switching (no router — routes array is empty)
- All HTTP calls point to `http://localhost:8080/api/`
- Prettier config: `singleQuote: true`, `printWidth: 100`, Angular HTML parser
- `skipTests: true` for all ng generate schematics (tests se escriben a mano como `*.spec.ts`)
- PWA: `public/manifest.webmanifest`, `public/sw.js` (cache shell + GET de `/api/`); ventas offline se guardan en `localStorage` y se sincronizan al reconectar (`flushOfflineSales`)

## Commands

| Action | Command |
|---|---|
| Run backend | `cd backend-springboot && ./mvnw spring-boot:run` |
| Run frontend | `cd frontend-angular && npm start` |
| Test backend | `cd backend-springboot && ./mvnw test` |
| Test frontend | `cd frontend-angular && npm test` |
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

- Backend: `./mvnw test` — `@SpringBootTest` por dominio (product, customer, category, sale/refunds, settings) + `JwtTokenServiceTests` + `AccountContextTests`
- Frontend: `npm test` → `ng test` con builder `@angular/build:unit-test` + Vitest (runner `vitest`, `vitest.config.ts` con entorno jsdom)
- Los tests de servicio del backend deben fijar `accountContext.setAccountId(...)` porque `requireAccountId()` es fail-closed

## CI

- GitHub Actions en `.github/workflows/ci.yml`: corre tests de backend y tests + build de frontend en cada push/PR a `main`
