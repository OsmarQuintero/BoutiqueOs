# Proyecto Boutique OS

Sistema SaaS para una boutique pequena de barrio operada por una sola persona.

## Objetivo

Ayudar al dueno a controlar ventas, inventario, compras, clientes y corte diario desde una web app simple, rapida y usable en celular.

## Stack

- Frontend: Angular.
- Backend: Java + Spring Boot.
- Base de datos inicial: H2 embebida.
- Base de datos futura recomendada: PostgreSQL.

## Documentos

- `PRD.md` - definicion del producto y alcance MVP.
- `flujo-trabajo.md` - operacion diaria del dueno.
- `benchmark.md` - referencias de SaaS similares.
- `arquitectura.md` - propuesta tecnica inicial.
- `backlog.md` - tareas iniciales para construir.

## Estructura

- `backend-springboot/` - API Java + Spring Boot + H2.
- `frontend-angular/` - app Angular.

## Ejecutar Backend

```bash
cd /home/osmariqv/Documentos/Proyecto/backend-springboot
./mvnw spring-boot:run
```

Endpoints iniciales:

- `GET /api/products`
- `GET /api/products/{id}`
- `POST /api/products`
- `PUT /api/products/{id}`
- `DELETE /api/products/{id}`
- `GET /api/sales/today`
- `POST /api/sales`

H2 Console:

- URL: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:file:./data/boutique-os`
- User: `sa`
- Password: vacio

## Ejecutar Frontend

```bash
cd /home/osmariqv/Documentos/Proyecto/frontend-angular
npm start
```

Angular queda disponible normalmente en:

- `http://localhost:4200`
