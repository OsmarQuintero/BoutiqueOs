# Deploy a Vercel + Render + Supabase

Esta es la ruta recomendada para produccion:

- `Vercel` para `frontend-angular/`
- `Render` para `backend-springboot/`
- `Supabase Postgres` para la base de datos

## Arquitectura final

- El frontend Angular se publica como sitio estatico en Vercel.
- El frontend consume la API usando `BOUTIQUE_API_URL`.
- El backend Spring Boot se publica en Render como servicio web.
- El backend se conecta a Supabase usando `SPRING_DATASOURCE_*`.
- CORS se controla con `APP_CORS_ALLOWED_ORIGINS`.

## 1. Crear Supabase

1. Crea un proyecto nuevo en Supabase.
2. Abre `Project Settings > Database`.
3. Copia estos datos:
   - `Host`
   - `Database name`
   - `Port`
   - `User`
   - `Password`
4. Forma la URL JDBC asi:

```text
jdbc:postgresql://db.xxx.supabase.co:5432/postgres?sslmode=require
```

Valores que vas a usar en Render:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://db.xxx.supabase.co:5432/postgres?sslmode=require
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=tu-password
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
SPRING_JPA_HIBERNATE_DDL_AUTO=update
SPRING_H2_CONSOLE_ENABLED=false
```

## 2. Publicar backend en Render

El repo ya trae [render.yaml](/home/osmariqv/Documentos/Proyecto/render.yaml), asi que Render puede leer la configuracion base automaticamente.

### Pasos

1. Entra a Render.
2. Elige `New + > Blueprint`.
3. Conecta el repositorio.
4. Render detectara `render.yaml`.
5. Crea el servicio `boutique-os-backend`.
6. Completa las variables:
   - `SPRING_DATASOURCE_URL`
   - `SPRING_DATASOURCE_USERNAME`
   - `SPRING_DATASOURCE_PASSWORD`
   - `APP_CORS_ALLOWED_ORIGINS`

### CORS recomendado

Usa algo asi cuando ya tengas la URL de Vercel:

```text
APP_CORS_ALLOWED_ORIGINS=http://localhost:4200,https://tu-frontend.vercel.app,https://*.vercel.app
```

### Validacion del backend

Cuando Render termine:

1. Abre la URL publica del servicio.
2. Prueba:

```text
https://tu-backend.onrender.com/api/products
```

Si responde JSON, el backend ya quedo arriba.

## 3. Publicar frontend en Vercel

El repo ya trae [frontend-angular/vercel.json](/home/osmariqv/Documentos/Proyecto/frontend-angular/vercel.json) y el build ya genera `public/runtime-config.js` con la URL de API.

### Pasos

1. Entra a Vercel.
2. Importa el repositorio.
3. En `Root Directory` selecciona `frontend-angular`.
4. Verifica estos valores:
   - Build Command: `npm run build`
   - Output Directory: `dist/frontend-angular/browser`
5. Agrega la variable:

```text
BOUTIQUE_API_URL=https://tu-backend.onrender.com/api
```

6. Despliega.

## 4. Amarrar frontend con backend

Cuando Vercel te de la URL final:

1. Copia la URL publica del frontend.
2. Regresa a Render.
3. Actualiza `APP_CORS_ALLOWED_ORIGINS`.
4. Haz redeploy del backend si Render no lo hace solo.

Ejemplo:

```text
APP_CORS_ALLOWED_ORIGINS=http://localhost:4200,https://boutique-os.vercel.app,https://*.vercel.app
```

## 5. Smoke test final

Con ambos arriba:

1. Abre el frontend en Vercel.
2. Inicia sesion.
3. Valida estos flujos:
   - Ver productos
   - Agregar a carrito
   - Cobrar venta
   - Revisar `Corte diario`
   - Crear cliente
   - Revisar inventario

Si algo falla:

- Error de red en frontend: casi siempre es `BOUTIQUE_API_URL`
- Error CORS: casi siempre es `APP_CORS_ALLOWED_ORIGINS`
- Error 500 en backend: casi siempre es `SPRING_DATASOURCE_*`

## 6. Variables finales

### Vercel

```text
BOUTIQUE_API_URL=https://tu-backend.onrender.com/api
```

### Render

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://db.xxx.supabase.co:5432/postgres?sslmode=require
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=tu-password
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
SPRING_JPA_HIBERNATE_DDL_AUTO=update
SPRING_H2_CONSOLE_ENABLED=false
APP_CORS_ALLOWED_ORIGINS=http://localhost:4200,https://tu-frontend.vercel.app,https://*.vercel.app
```

## Notas importantes

- `Render free` puede dormir el backend despues de inactividad.
- `SPRING_JPA_HIBERNATE_DDL_AUTO=update` sirve para arrancar rapido, pero despues conviene migrar a una estrategia con migraciones formales.
- Si usas dominio propio en Vercel, agregalo tambien en `APP_CORS_ALLOWED_ORIGINS`.
- Si quieres bloquear previews de Vercel, quita `https://*.vercel.app`.
