# Arquitectura Inicial

## Enfoque

Web app responsive, mobile-first, pensada para uso diario desde celular y laptop.

## Modulos

- Autenticacion.
- Productos.
- Inventario.
- Ventas.
- Compras.
- Clientes.
- Reportes.

## Entidades Principales

### users

- id
- name
- email
- password_hash
- created_at

### products

- id
- name
- category
- size
- color
- cost_price
- sale_price
- sku
- image_url
- status
- created_at

### inventory_movements

- id
- product_id
- type: purchase, sale, adjustment, return
- quantity
- unit_cost
- note
- created_at

### sales

- id
- total
- discount
- payment_method
- customer_id
- created_at

### sale_items

- id
- sale_id
- product_id
- quantity
- unit_price
- unit_cost

### customers

- id
- name
- phone
- notes
- created_at

### purchases

- id
- supplier_name
- total_cost
- note
- created_at

## Stack Definido

Stack elegido para el prototipo:

- Frontend: Angular.
- Backend: Java + Spring Boot.
- Base de datos inicial: H2 embebida.
- Auth inicial: email/password con JWT o sesion simple.
- Deploy inicial: local durante desarrollo.

## Estructura Recomendada

```text
Proyecto/
  docs/
  frontend-angular/
  backend-springboot/
```

## Backend Spring Boot

Modulos iniciales:

- `product` - productos y variantes simples.
- `inventory` - movimientos de inventario.
- `sale` - ventas y partidas.
- `customer` - clientes.
- `report` - corte diario y reportes.

Capas:

- Controller: endpoints REST.
- Service: reglas de negocio.
- Repository: acceso a datos con Spring Data JPA.
- Entity: tablas/modelos.
- DTO: entradas y salidas para API.

Dependencias recomendadas:

- Spring Web.
- Spring Data JPA.
- H2 Database.
- Validation.
- Lombok opcional.
- Spring Security cuando se agregue login.

## Frontend Angular

Modulos o features iniciales:

- Dashboard.
- Productos.
- Nueva venta.
- Inventario.
- Clientes.
- Corte diario.

Estructura recomendada:

```text
src/app/
  core/
  shared/
  features/
    dashboard/
    products/
    sales/
    inventory/
    customers/
    reports/
```

## H2 Durante El Prototipo

Ventajas:

- Arranque rapido.
- Sin instalar servidor de base de datos.
- Ideal para validar pantallas, entidades y flujo.

Limitaciones:

- No debe ser la base final de produccion.
- Puede perder datos si se configura en memoria.
- Para pruebas reales conviene usar H2 en archivo.

Configuracion sugerida:

```properties
spring.datasource.url=jdbc:h2:file:./data/boutique-os
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update
spring.h2.console.enabled=true
```

Base final recomendada para produccion:

- PostgreSQL.

## Principios

- Pocas pantallas.
- Acciones rapidas.
- Cada cambio de inventario debe generar movimiento.
- Reportes entendibles, no contabilidad compleja.
- Nada de funciones empresariales hasta validar el MVP.
