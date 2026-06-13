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

## Despliegue

El proyecto ya queda preparado para esta combinacion:

- Frontend en Vercel.
- Backend en Render.
- Base de datos PostgreSQL en Supabase.

Guia paso a paso:

- `DEPLOY.md` - despliegue completo en Vercel + Render + Supabase.

### Variables de entorno

Hay un archivo base en `.env.example` con las variables que necesitas.

Frontend en Vercel:

- `BOUTIQUE_API_URL=https://tu-backend.onrender.com/api`

Backend en Render:

- `SPRING_DATASOURCE_URL=jdbc:postgresql://db.xxx.supabase.co:5432/postgres?sslmode=require`
- `SPRING_DATASOURCE_USERNAME=postgres`
- `SPRING_DATASOURCE_PASSWORD=tu-password`
- `SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver`
- `SPRING_JPA_HIBERNATE_DDL_AUTO=update`
- `SPRING_H2_CONSOLE_ENABLED=false`
- `APP_CORS_ALLOWED_ORIGINS=http://localhost:4200,https://tu-frontend.vercel.app`

### Supabase

1. Crea un proyecto PostgreSQL en Supabase.
2. Copia la cadena de conexion Postgres y usala como `SPRING_DATASOURCE_URL`.
3. Agrega `?sslmode=require` si tu URL no lo trae.

### Render

1. Importa el repositorio en Render.
2. Usa el archivo `render.yaml` del root.
3. Completa las variables `SPRING_DATASOURCE_*` con los datos de Supabase.
4. En `APP_CORS_ALLOWED_ORIGINS` agrega tu URL final de Vercel y cualquier preview que quieras permitir, por ejemplo:
   `https://tu-frontend.vercel.app,https://*.vercel.app`

### Vercel

1. Importa el repositorio en Vercel usando como Root Directory `frontend-angular/`.
2. Vercel ya puede usar `frontend-angular/vercel.json`.
3. Configura `BOUTIQUE_API_URL` con la URL publica de Render terminando en `/api`.
4. Despliega.

### Notas

- En local se sigue usando H2 por defecto.
- En produccion el backend toma `PORT` automaticamente.
- El frontend ya no depende de `localhost`; toma la API desde `BOUTIQUE_API_URL` o usa `/api` solo como fallback.
