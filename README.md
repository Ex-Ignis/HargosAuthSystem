# 🔐 Hargos Auth System

Sistema de autenticación multi-tenant con gestión de usuarios, organizaciones y límites de riders.

## 🚀 Inicio Rápido

### 1. Levantar Base de Datos

```bash
docker-compose up -d
```

Esto crea:
- PostgreSQL en puerto 5432
- Base de datos `hargos_auth_db`
- Schema `auth` con todas las tablas
- Datos de ejemplo

### 2. Iniciar Aplicación

```bash
mvn spring-boot:run
```

La aplicación estará en: `http://localhost:8081`

---

## 📊 Estructura de Base de Datos

### Tablas Principales

- **`organizations`** - Empresas clientes (Arendel, Entregalia, etc.) + HARGOS_SYSTEM (organización interna)
- **`apps`** - Tipos de servicios (Riders Management, Warehouse, etc.) + SYSTEM (app interna)
- **`tenants`** - Instancias de servicios para organizaciones
  - `account_limit`: Límite de cuentas de usuario
  - `rider_limit`: Límite de riders (NULL = ilimitado) - validado por el droplet
  - **Tenant especial GLOBAL**: Para superadministradores del sistema
- **`users`** - Usuarios del sistema
- **`user_tenant_roles`** - Roles de usuarios por tenant (SUPER_ADMIN, TENANT_ADMIN, USER)
- **`refresh_tokens`** - Tokens JWT de refresh

### Jerarquía

```
Organization (HARGOS_SYSTEM) - Sistema Interno
  └── Tenant (GLOBAL)
      └── Users
          └── SUPER_ADMIN (admin@hargos.es)

Organization (Arendel) - Cliente
  └── Tenant (Riders Management para Arendel)
      ├── account_limit: 100 cuentas
      ├── rider_limit: 500 riders
      └── Users
          ├── TENANT_ADMIN (1/100)
          └── USER (99/100)
```

---

## 🔒 Sistema de Roles

### SUPER_ADMIN (tú)
- Acceso total al sistema
- Endpoints: `/api/admin/**`
- Puede crear organizaciones, tenants y usuarios
- **Asignado al tenant GLOBAL** (organización HARGOS_SYSTEM)
- No necesita asignación individual a cada tenant para gestionarlos

### TENANT_ADMIN
- Gestiona usuarios de SUS tenants únicamente
- Endpoints: `/api/tenant-admin/**`
- Puede crear usuarios hasta el `account_limit`

### USER
- Usuario regular sin privilegios administrativos

---

## 📡 Endpoints Principales

### Admin (SUPER_ADMIN)

```http
# Organizaciones
POST   /api/admin/organizations
GET    /api/admin/organizations
GET    /api/admin/organizations/{id}
DELETE /api/admin/organizations/{id}

# Tenants
POST   /api/admin/tenants
GET    /api/admin/tenants
GET    /api/admin/tenants/{id}
GET    /api/admin/organizations/{orgId}/tenants
DELETE /api/admin/tenants/{id}

# Usuarios
POST   /api/admin/users
GET    /api/admin/users
GET    /api/admin/users/{id}
POST   /api/admin/users/{id}/tenants
PUT    /api/admin/users/{id}/activate
PUT    /api/admin/users/{id}/deactivate
DELETE /api/admin/users/{id}
```

### Tenant Admin (TENANT_ADMIN)

```http
# Usuarios (solo de sus tenants)
POST   /api/tenant-admin/users
GET    /api/tenant-admin/users
GET    /api/tenant-admin/users/{id}
POST   /api/tenant-admin/users/{id}/tenants
PUT    /api/tenant-admin/users/{id}/activate
PUT    /api/tenant-admin/users/{id}/deactivate
DELETE /api/tenant-admin/users/{id}

# Tenants (solo los que gestiona)
GET    /api/tenant-admin/tenants
GET    /api/tenant-admin/tenants/{id}
GET    /api/tenant-admin/tenants/{id}/users
```

### Droplet (público - desde app externa)

```http
# Validar límite de riders
POST   /api/droplet/validate-rider-limit
{
  "tenantId": 1,
  "currentRiderCount": 150
}

# Obtener información del tenant
GET    /api/droplet/tenant-info/{tenantId}
```

---

## 🎯 Sistema de Límites

Cada tenant tiene **dos límites**:

### 1. Account Limit (cuentas de usuario)
- Número máximo de usuarios (incluye TENANT_ADMIN)
- Mínimo: 1
- Ejemplo: `account_limit: 100` = 1 admin + 99 usuarios

### 2. Rider Limit (riders)
- Número máximo de riders que puede tener el tenant en servicio
- `NULL` = ilimitado
- Ejemplo: `rider_limit: 500` = máximo 500 riders
- El **droplet** envía el número actual de riders para validación

**Validaciones automáticas**:
- Al crear usuario se verifica `account_limit`
- El droplet valida `rider_limit` antes de crear/mostrar riders
- Si se excede → HTTP 403 con mensaje claro

---

## 👤 Usuarios de Prueba

El sistema viene con usuarios de prueba precargados:

### **SUPER_ADMIN** (Tú)
- **Email**: `admin@hargos.es`
- **Password**: `SuperAdmin123!`
- **Acceso**: Total al sistema

### **TENANT_ADMIN** (Administradores de empresas)

#### Arendel
- **Email**: `admin@arendel.com`
- **Password**: `ArendelAdmin123!`
- **Tenants**: Riders Arendel, Riders Demo

#### Entregalia
- **Email**: `admin@entregalia.com`
- **Password**: `EntregaliaAdmin123!`
- **Tenant**: Riders Entregalia

### **USER** (Usuarios regulares)

| Email | Password | Tenant | Cargo | Estado |
|-------|----------|--------|-------|--------|
| `coordinador@arendel.com` | `User123!` | Riders Arendel | Coordinador | ✅ Activo |
| `supervisor@arendel.com` | `User123!` | Riders Arendel | Supervisor | ✅ Activo |
| `dispatcher@entregalia.com` | `User123!` | Riders Entregalia | Dispatcher | ✅ Activo |
| `antiguo@arendel.com` | `User123!` | Riders Arendel | Antiguo | ❌ Inactivo |
| `nuevo@entregalia.com` | `User123!` | Riders Entregalia | Nuevo | ⚠️ Email no verificado |

### **Casos de Prueba Incluidos**

1. **Multi-tenant**: `admin@arendel.com` tiene acceso a 2 tenants (Arendel y Demo)
2. **Usuario inactivo**: `antiguo@arendel.com` (is_active = false)
3. **Email no verificado**: `nuevo@entregalia.com` (email_verified = false)

---

## 🛡️ Seguridad

### @PreAuthorize
- **AdminController**: `@PreAuthorize("@authz.isSuperAdmin()")`
- **TenantAdminController**: `@PreAuthorize("@authz.isTenantAdmin()")`
- **RiderController**: Público (sin autenticación)

### Validaciones
- TENANT_ADMIN solo gestiona usuarios de SUS tenants
- TENANT_ADMIN NO puede ver usuarios de otros tenants
- Verificación de límites en cada creación

---

## 📝 Ejemplo Completo

### 1. Crear Organización (SUPER_ADMIN)

```bash
curl -X POST http://localhost:8081/api/admin/organizations \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Mi Empresa",
    "description": "Nueva empresa"
  }'
```

### 2. Crear Tenant con Límites (SUPER_ADMIN)

```bash
curl -X POST http://localhost:8081/api/admin/tenants \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "appId": 1,
    "organizationId": 1,
    "name": "Riders Mi Empresa",
    "accountLimit": 50,
    "riderLimit": 200
  }'
```

### 3. Crear TENANT_ADMIN (SUPER_ADMIN)

```bash
curl -X POST http://localhost:8081/api/admin/users \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@miempresa.com",
    "password": "password123",
    "fullName": "Admin Mi Empresa",
    "tenantRoles": [
      {
        "tenantId": 1,
        "role": "TENANT_ADMIN"
      }
    ]
  }'
```

### 4. Validar Límite de Riders (desde droplet)

El droplet llama a este endpoint antes de mostrar el formulario de crear rider:

```bash
curl -X POST http://localhost:8081/api/droplet/validate-rider-limit \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": 1,
    "currentRiderCount": 150
  }'
```

**Respuesta si está OK** (HTTP 200):
```json
{
  "withinLimit": true,
  "currentCount": 150,
  "limit": 500,
  "remaining": 350,
  "message": "OK"
}
```

**Respuesta si excede límite** (HTTP 403):
```json
{
  "withinLimit": false,
  "currentCount": 501,
  "limit": 500,
  "remaining": -1,
  "message": "Límite de riders excedido. Máximo permitido: 500"
}
```

---

## 🔄 Reiniciar Base de Datos

```bash
# Detener contenedor
docker-compose down

# Borrar volumen (BORRA TODOS LOS DATOS)
docker volume rm hargosauthdystem_postgres_auth_data

# Levantar de nuevo (ejecuta init-db.sql)
docker-compose up -d
```

---

## 🧪 Verificar Estado

```bash
# Conectarse a PostgreSQL
docker exec -it Hargos-Auth-System psql -U hargosauth -d hargos_auth_db

# Ver tablas
\dt auth.*

# Ver datos
SELECT * FROM auth.organizations;
SELECT * FROM auth.tenants;
SELECT * FROM auth.users;

# Ver tenants con límites
SELECT
  t.name,
  o.name as org,
  t.account_limit,
  COUNT(utr.id) as current_accounts,
  t.rider_limit,
  COUNT(r.id) as current_riders
FROM auth.tenants t
LEFT JOIN auth.organizations o ON t.organization_id = o.id
LEFT JOIN auth.user_tenant_roles utr ON utr.tenant_id = t.id
LEFT JOIN auth.riders r ON r.tenant_id = t.id AND r.is_active = true
GROUP BY t.id, t.name, o.name, t.account_limit, t.rider_limit;
```

---

## 🧪 Pruebas Rápidas

### 1. Login como SUPER_ADMIN
```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@hargos.es",
    "password": "SuperAdmin123!"
  }'
```

### 2. Login como TENANT_ADMIN de Arendel
```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@arendel.com",
    "password": "ArendelAdmin123!"
  }'
```

### 3. Ver usuarios de Arendel (como TENANT_ADMIN)
```bash
# Primero hacer login y guardar el token
TOKEN="tu_token_aqui"

curl -X GET http://localhost:8081/api/tenant-admin/users \
  -H "Authorization: Bearer $TOKEN"
```

### 4. Validar límite de riders desde droplet
```bash
curl -X POST http://localhost:8081/api/droplet/validate-rider-limit \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": 1,
    "currentRiderCount": 450
  }'
```

### 5. Ver todos los usuarios (como SUPER_ADMIN)
```bash
curl -X GET http://localhost:8081/api/admin/users \
  -H "Authorization: Bearer $TOKEN"
```

---

## 📚 Tecnologías

- **Java 17+**
- **Spring Boot 3**
- **Spring Security** con `@PreAuthorize`
- **PostgreSQL 15**
- **Docker & Docker Compose**
- **JWT** para autenticación
- **BCrypt** para hashing de passwords

---

## ⚠️ Notas Importantes

1. **Endpoints del Droplet son públicos** - Deberías implementar API keys para autenticación entre servicios
2. **Hash de contraseña** - Genera uno real con `PasswordHashGenerator.java`
3. **Multi-tenant** - Un usuario puede pertenecer a múltiples tenants con diferentes roles
4. **Límites** - Se validan automáticamente al crear usuarios
5. **Riders** - El droplet envía el número actual de riders para validación, no los almacenamos
6. **Cascading deletes** - Borrar una organización borra sus tenants y relaciones

---

## 📝 Flujos Completos de Usuario

### 1️⃣ Flujo de Registro y Compra (Cliente)

**Paso 1: Registro sin tenant**
```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "cliente@empresa.com",
    "password": "MiPassword123!",
    "fullName": "Juan Pérez"
  }'
```

**Respuesta:**
```json
{
  "id": 10,
  "email": "cliente@empresa.com",
  "fullName": "Juan Pérez",
  "isActive": true,
  "emailVerified": false,
  "tenants": [],  // Sin tenants asignados
  "createdAt": "2025-01-15T10:30:00"
}
```

**Paso 2: Login**
```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "cliente@empresa.com",
    "password": "MiPassword123!"
  }'
```

**Paso 3: Comprar un producto (crea organización + tenant)**
```bash
curl -X POST http://localhost:8081/api/purchase \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "organizationName": "Mi Empresa SL",
    "organizationDescription": "Empresa de logística",
    "appId": 2,
    "tenantName": "Riders Mi Empresa",
    "tenantDescription": "Servicio de gestión de riders",
    "accountLimit": 50,
    "ridersConfig": {
      "riderLimit": 200,
      "deliveryZones": 5,
      "maxDailyDeliveries": 500,
      "realTimeTracking": true,
      "smsNotifications": true
    }
  }'
```

**Resultado:**
- ✅ Se crea la organización "Mi Empresa SL"
- ✅ Se crea el tenant "Riders Mi Empresa"
- ✅ El usuario se convierte en TENANT_ADMIN del tenant
- ✅ Ahora puede gestionar usuarios de su tenant

---

### 2️⃣ Flujo de Empleado Creado por Admin

**Opción A: TENANT_ADMIN crea el empleado directamente**
```bash
curl -X POST http://localhost:8081/api/tenant-admin/users \
  -H "Authorization: Bearer {admin_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "empleado@empresa.com",
    "password": "EmpleadoPass123!",
    "fullName": "María García",
    "tenantRoles": [
      {
        "tenantId": 1,
        "role": "USER"
      }
    ]
  }'
```

---

### 3️⃣ Flujo de Empleado que se Registra Solo

**Paso 1: Empleado se registra**
```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "empleado2@empresa.com",
    "password": "Password123!",
    "fullName": "Carlos López"
  }'
```

**Paso 2: TENANT_ADMIN busca usuarios disponibles**
```bash
curl -X GET http://localhost:8081/api/tenant-admin/available-users \
  -H "Authorization: Bearer {admin_token}"
```

**Respuesta:**
```json
[
  {
    "id": 15,
    "email": "empleado2@empresa.com",
    "fullName": "Carlos López",
    "isActive": true,
    "emailVerified": false,
    "tenants": [],  // Sin tenant
    "createdAt": "2025-01-15T11:00:00"
  }
]
```

**Paso 3: TENANT_ADMIN asigna el empleado a su tenant**
```bash
curl -X POST http://localhost:8081/api/tenant-admin/users/15/tenants \
  -H "Authorization: Bearer {admin_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": 1,
    "role": "USER"
  }'
```

---

### 4️⃣ Compra de Múltiples Productos

Un cliente puede comprar diferentes productos para su organización:

**Comprar Warehouse Management**
```bash
curl -X POST http://localhost:8081/api/purchase \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "organizationName": "Mi Empresa SL",
    "appId": 3,
    "tenantName": "Warehouse Mi Empresa",
    "accountLimit": 30,
    "warehouseConfig": {
      "warehouseCapacityM3": 5000.00,
      "loadingDocks": 8,
      "inventorySkuLimit": 10000,
      "barcodeScanning": true,
      "rfidEnabled": true,
      "temperatureControlledZones": 3
    }
  }'
```

**Comprar Fleet Management**
```bash
curl -X POST http://localhost:8081/api/purchase \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "organizationName": "Mi Empresa SL",
    "appId": 4,
    "tenantName": "Fleet Mi Empresa",
    "accountLimit": 20,
    "fleetConfig": {
      "vehicleLimit": 50,
      "gpsTracking": true,
      "maintenanceAlerts": true,
      "fuelMonitoring": true,
      "driverScoring": true,
      "telematicsEnabled": false
    }
  }'
```

---

## 🔄 Endpoints Nuevos

### Registro y Compra

```http
# Registro público (sin tenant)
POST   /api/auth/register

# Compra de producto (requiere autenticación)
POST   /api/purchase
```

### Gestión de Empleados (TENANT_ADMIN)

```http
# Ver usuarios disponibles para asignar (sin tenant)
GET    /api/tenant-admin/available-users

# Asignar usuario existente a un tenant
POST   /api/tenant-admin/users/{id}/tenants
```

---

## 🚧 TODOs Futuros

- [ ] Sistema de invitaciones por email con tokens
- [ ] Verificación de email
- [ ] Recuperación de contraseña
- [ ] Implementar API keys para endpoints de ritrack
- [ ] Rate limiting por tenant
- [ ] Sistema de auditoría/logging
- [ ] Alertas cuando se esté cerca del límite
- [ ] Dashboard para TENANT_ADMIN
- [ ] Sistema de upgrades de planes
- [ ] Pasarela de pago para compras
