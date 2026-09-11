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
4. Usa una sola modalidad de conexion. No mezcles valores entre `Direct connection` y `Session/Transaction pooler`.

### Opcion A: Direct connection

Forma la URL JDBC asi:

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

### Opcion B: Pooler de Supabase

Si prefieres el pooler, usa los datos del pooler completos:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:6543/postgres?sslmode=require
SPRING_DATASOURCE_USERNAME=postgres.xxx
SPRING_DATASOURCE_PASSWORD=tu-password
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
SPRING_JPA_HIBERNATE_DDL_AUTO=update
SPRING_H2_CONSOLE_ENABLED=false
```

`postgres.xxx` es solo ejemplo. Debes copiar el usuario exacto que te da Supabase para el pooler.

### Error comun

Si Render muestra algo como `tenant/user ... not found` o `Unable to determine Dialect without JDBC metadata`, casi siempre significa que la conexion a Postgres esta mal armada:

- pusiste el usuario del pooler con el host directo
- pusiste el host del pooler con el usuario directo
- copiaste mal la URL JDBC
- pegaste una URL o usuario incompleto en `SPRING_DATASOURCE_URL` o `SPRING_DATASOURCE_USERNAME`

## 2. Publicar backend en Render

El repo ya trae [render.yaml](/home/osmariqv/BoutiqueOs/render.yaml), asi que Render puede leer la configuracion base automaticamente.

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

El repo ya trae [frontend-angular/vercel.json](/home/osmariqv/BoutiqueOs/frontend-angular/vercel.json) y el build ya genera `public/runtime-config.js` con la URL de API.

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

## Cobro y activacion de cuenta

Cuando alguien compra desde la landing, la cuenta todavia no existe, asi que el
checkout no puede mandar `account_id`. El pago se registra por dos vias
independientes y cualquiera de las dos basta:

1. **Redirect del navegador** a `APP_FRONTEND_URL/?session_id=...` (camino feliz).
2. **Webhook de Stripe** `checkout.session.completed`, que registra el pago y le
   manda al cliente el enlace de activacion por correo.

La segunda existe porque la primera se pierde si el cliente cierra la pestaña.
**Sin el webhook configurado, un cliente que pague y cierre la pestaña queda sin
registro en el sistema.**

### Configurar el webhook

1. En Stripe: Developers -> Webhooks -> Add endpoint.
2. URL: `https://tu-backend.onrender.com/api/subscription/webhook`
3. Eventos: `checkout.session.completed`, `customer.subscription.updated`,
   `customer.subscription.deleted`, `invoice.payment_failed`.
4. Copia el signing secret a `STRIPE_WEBHOOK_SECRET`.

Sin `STRIPE_WEBHOOK_SECRET` el endpoint rechaza todo lo que llegue.

### Correo de activacion

```text
APP_MAIL_FROM=hola@tudominio.com
MAIL_HOST=smtp.tuproveedor.com
MAIL_PORT=587
MAIL_USERNAME=...
MAIL_PASSWORD=...
```

Si no configuras SMTP el sistema no truena: escribe el enlace de activacion en
el log con nivel WARN para que puedas rescatar la venta a mano. El enlace vive
7 dias.

### Crear tu cuenta de dueño

Ya no existen credenciales por defecto: una instalacion nueva no acepta
`admin/admin` ni ninguna otra combinacion hasta que se provisione una cuenta.
Para crear la tuya (o restablecer su contraseña si la olvidas):

```sh
curl -X POST "https://tu-backend.onrender.com/api/admin/owner-account" \
  -H "X-Admin-Secret: $APP_ADMIN_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"username":"tu@correo.com","password":"unaContrasenaLarga","storeName":"BoutiqueOS"}'
```

Queda con rol admin y plan PRO, o sea acceso a todo. La contraseña debe tener al
menos 12 caracteres y se guarda hasheada. Requiere `APP_ADMIN_SECRET`
configurada; sin ella el endpoint responde 403 y no hay forma de entrar.

### Reparar cuentas que pagaron y quedaron sin plan

Un checkout sin `metadata.plan` dejaba la suscripcion con `plan = null`, y con el
plan en null el sistema responde "No tienes una suscripcion activa" y bloquea
todo aunque el cliente siga pagando. La causa esta corregida (`PlanResolver`),
pero los registros que ya quedaron asi hay que repararlos.

Primero revisa sin tocar nada (`dryRun` viene activado por defecto):

```sh
curl -X POST "https://tu-backend.onrender.com/api/admin/subscriptions/repair" \
  -H "X-Admin-Secret: $APP_ADMIN_SECRET"
```

Si el listado se ve bien, aplica los cambios:

```sh
curl -X POST "https://tu-backend.onrender.com/api/admin/subscriptions/repair?dryRun=false" \
  -H "X-Admin-Secret: $APP_ADMIN_SECRET"
```

Asigna BASIC y estado ACTIVE. Si alguna de esas cuentas habia pagado Pro,
ajustala a mano despues. Es idempotente.

## Verificacion en dos pasos y sesiones

Desde septiembre de 2026, entrar desde un dispositivo nuevo, recuperar la
contraseña y cambiarla piden un codigo de 6 digitos que llega por correo (vence
en 10 minutos, 5 intentos). Al entrar se puede marcar "recordar este dispositivo"
y no se vuelve a pedir en 30 dias.

**Por eso el correo SMTP ya es obligatorio** (`APP_MAIL_FROM` y `MAIL_*`). Sin
SMTP los codigos no llegan; como respaldo quedan escritos en el log de Render
con nivel WARN ("Codigo de verificacion ... para ..."), para que la dueña pueda
entrar mientras se configura el correo.

El codigo va al usuario si es un correo; si no, al "correo de contacto" de
Datos del negocio. Una cuenta sin ningun correo entra sin segundo paso y la
pantalla le pide agregar uno.

Cambiar la contraseña (desde Ajustes o recuperandola) **cierra todas las
sesiones abiertas** y hace que todos los dispositivos recordados vuelvan a pedir
codigo.

### Clave de sesiones

`APP_JWT_SECRET` firma las sesiones. Si falta, el servidor inventa una al
arrancar y cada reinicio o deploy saca a todas las clientas. En Render el
blueprint la genera sola (`generateValue`) y `APP_JWT_REQUIRE_SECRET=true` hace
que el servidor **no arranque** si falta, en lugar de fallar en silencio.

## Notas importantes

- `Render free` puede dormir el backend despues de inactividad. Stripe reintenta
  los webhooks ante fallos, asi que un backend dormido no pierde el evento.
- `SPRING_JPA_HIBERNATE_DDL_AUTO=update` sirve para arrancar rapido, pero despues conviene migrar a una estrategia con migraciones formales.
- Si usas dominio propio en Vercel, agregalo tambien en `APP_CORS_ALLOWED_ORIGINS`.
- Si quieres bloquear previews de Vercel, quita `https://*.vercel.app`.

## Planes mensual y anual (Stripe)

Variables nuevas en Render (ademas de `STRIPE_PRICE_BASIC` y `STRIPE_PRICE_PRO`, que son las mensuales):

| Variable | Que es |
|---|---|
| `STRIPE_PRICE_BASIC_ANNUAL` | Precio anual del Basico ($4,990 MXN, 2 meses gratis) |
| `STRIPE_PRICE_PRO_ANNUAL` | Precio anual del Pro ($9,990 MXN, 2 meses gratis) |

En Stripe hay que tener:

- **Portal de clientes** configurado (Settings > Billing > Customer portal): cambiar de plan y de periodo entre los cuatro precios, actualizar tarjeta, ver facturas y cancelar al final del periodo. El boton "Administrar suscripcion" del sistema lo abre.
- En el webhook `/api/subscription/webhook`, los eventos `checkout.session.completed`, `customer.subscription.updated`, `customer.subscription.deleted`, `invoice.payment_failed` y **`invoice.paid`** (sin este, una cuenta que se puso al corriente seguiria marcada como vencida).

Reglas de cobro que aplica el sistema: con pago vencido hay 7 dias de gracia; despues la cuenta queda en solo lectura (puede consultar y descargar su respaldo, no vender) hasta que pague. Cancelar deja todo activo hasta el fin del periodo pagado. El plan Pro incluye 3 usuarios de caja activos.
