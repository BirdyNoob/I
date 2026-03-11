# Icentric — Platform Backend

A **multi-tenant SaaS platform backend** built with Spring Boot 4 and Java 21. It handles platform-admin authentication (with optional TOTP MFA), automated tenant provisioning (schema-per-tenant via Flyway), and a full admin-impersonation workflow backed by JWT.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 4.0.3 |
| Language | Java 21 |
| Security | Spring Security + JWT (jjwt 0.11.5) |
| MFA | TOTP via `dev.samstevens.totp` |
| Database | PostgreSQL |
| ORM | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| Boilerplate | Lombok |

---

## Project Structure

```
src/main/java/com/icentric/Icentric/
├── IcentricApplication.java            # Spring Boot entry point
│
├── config/
│   ├── PasswordConfig.java             # BCryptPasswordEncoder bean
│   └── SecurityConfig.java             # Security filter chain
│
├── security/
│   ├── JwtService.java                 # Token generation & parsing
│   ├── JwtAuthenticationFilter.java    # Per-request JWT validation
│   └── MfaService.java                 # TOTP secret/QR/verification
│
├── tenant/
│   ├── TenantContext.java              # ThreadLocal tenant holder
│   └── TenantFilter.java              # Sets PostgreSQL search_path per request
│
└── platform/
    ├── admin/
    │   ├── controller/PlatformAuthController.java
    │   ├── dto/{PlatformLoginRequest, PlatformLoginResponse}.java
    │   ├── entity/PlatformAdmin.java
    │   ├── repository/PlatformAdminRepository.java
    │   └── service/PlatformAuthService.java
    │
    ├── tenant/
    │   ├── controller/PlatformTenantController.java
    │   ├── dto/CreateTenantRequest.java
    │   ├── entity/Tenant.java
    │   ├── repository/TenantRepository.java
    │   └── service/
    │       ├── TenantService.java               # Orchestrates provisioning
    │       ├── TenantProvisioningService.java   # Creates PG schema + runs Flyway
    │       └── TenantUserBootstrapService.java  # Seeds first super_admin user
    │
    └── impersonation/
        ├── dto/ImpersonationRequest.java
        ├── entity/ImpersonationSession.java
        ├── repository/ImpersonationSessionRepository.java
        └── service/ImpersonationService.java

src/main/resources/
├── application.properties
└── db/migration/
    ├── system/          # Migrations for the shared `system` schema
    │   ├── V3__create_system_tenants_table.sql
    │   ├── V4__platform_admins.sql
    │   ├── V5__platform_admin_mfa.sql
    │   └── V6__impersonation_sessions.sql
    └── tenant/          # Template migrations run for each new tenant schema
        └── V1__tenant_base_tables.sql
```

---

## Database Architecture

### Schema Strategy (Schema-per-Tenant)

```
PostgreSQL
├── system                          ← Shared platform schema (Flyway-managed)
│   ├── tenants
│   ├── platform_admins
│   └── impersonation_sessions
│
├── tenant_acme                     ← Isolated schema per tenant
│   └── users
│
└── tenant_globex                   ← Another tenant's isolated schema
    └── users
```

The correct schema is activated per request by executing `SET search_path TO <schema>` via `TenantFilter` (see [Request Lifecycle](#request-lifecycle)).

### System Schema Tables

#### `system.tenants`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `slug` | TEXT UNIQUE NOT NULL | Used as schema suffix (`tenant_<slug>`) |
| `company_name` | TEXT | |
| `plan` | TEXT | e.g. `starter`, `pro` |
| `max_seats` | INTEGER | |
| `status` | TEXT | Default: `active` |
| `created_at` | TIMESTAMPTZ | |
| `trial_ends_at` | TIMESTAMPTZ | |

#### `system.platform_admins`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `email` | VARCHAR(255) UNIQUE NOT NULL | |
| `password_hash` | TEXT NOT NULL | BCrypt |
| `full_name` | VARCHAR(255) | |
| `is_active` | BOOLEAN | Default: `true` |
| `mfa_enabled` | BOOLEAN | Default: `false` |
| `mfa_secret` | TEXT | TOTP secret (added in V5) |
| `last_login_at` | TIMESTAMPTZ | |
| `created_at` | TIMESTAMPTZ | Default: `NOW()` |

#### `system.impersonation_sessions`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `platform_admin_id` | UUID | Who initiated |
| `impersonated_user_id` | UUID | Who was impersonated |
| `tenant_slug` | TEXT | Which tenant |
| `reason` | TEXT | Justification text |
| `started_at` | TIMESTAMPTZ | Default: `NOW()` |
| `ended_at` | TIMESTAMPTZ | Nullable |
| `actions_taken` | INT | Default: `0` |

### Tenant Schema Tables

#### `<tenant_schema>.users`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `email` | TEXT UNIQUE | |
| `role` | TEXT | e.g. `super_admin` |
| `created_at` | TIMESTAMPTZ | |

---

## API Reference

> **Base URL:** `http://localhost:8080`
>
> All endpoints except those under `/api/v1/platform/auth/**` and `POST /api/v1/platform/tenants/{slug}/impersonate` require a valid `Authorization: Bearer <token>` header.

---

### Platform Auth — `/api/v1/platform/auth`

#### `POST /api/v1/platform/auth/login`
Authenticate a platform admin. If MFA is enabled on the account, a valid TOTP code must be supplied.

**Request Body**
```json
{
  "email": "admin@example.com",
  "password": "secret",
  "mfaCode": "123456"
}
```
> `mfaCode` is only required when MFA has been enrolled.

**Response `200 OK`**
```json
{
  "token": "<JWT>"
}
```

**Errors**
| Status | Reason |
|---|---|
| `500` | Invalid credentials or invalid MFA code (runtime exception — should be refined to `401`) |

---

#### `POST /api/v1/platform/auth/mfa/enroll?email={email}`
Generate and store a TOTP secret for the given platform admin, returning a PNG QR code to scan with an authenticator app.

**Query Param** — `email` (string, required)

**Response `200 OK`** — `image/png` bytes (QR code)

> Once enrolled, subsequent logins must include the 6-digit TOTP code.

---

### Platform Tenants — `/api/v1/platform/tenants`

> Requires `ROLE_PLATFORM_ADMIN` JWT.

#### `POST /api/v1/platform/tenants`
Create a new tenant. Automatically:
1. Persists the tenant record in `system.tenants`.
2. Creates a PostgreSQL schema `tenant_<slug>`.
3. Runs all migrations in `db/migration/tenant/` against the new schema.
4. Inserts the first `super_admin` user into `<tenant_schema>.users`.

**Request Body**
```json
{
  "slug": "acme",
  "companyName": "Acme Corp",
  "adminEmail": "owner@acme.com"
}
```

**Response `200 OK`** — The created `Tenant` entity

**Errors**
| Status | Reason |
|---|---|
| `500` | `slug` already exists |

---

#### `POST /api/v1/platform/tenants/{slug}/impersonate`

> **Note:** This endpoint is publicly accessible (no JWT required) in the current security config. This is likely intentional for initial bootstrapping, but should be reviewed before going to production.

Start an impersonation session. Records the session in `system.impersonation_sessions` and returns a short-lived impersonation JWT (2-hour expiry) that can be used to act on behalf of the target tenant user.

**Path Param** — `slug` (tenant slug)

**Request Body**
```json
{
  "targetUserId": "550e8400-e29b-41d4-a716-446655440000",
  "reason": "Customer requested help with onboarding"
}
```

**Response `200 OK`** — Impersonation JWT string

The returned token carries additional claims:
- `impersonatedBy` — UUID of the platform admin
- `impersonationSessionId` — UUID of the `impersonation_sessions` row

---

## Security Model

### Filter Chain Order

```
Request
  │
  ▼
JwtAuthenticationFilter          ← Validates Bearer token, extracts claims,
  │   (runs first)                  populates SecurityContext + TenantContext
  │
  ▼
TenantFilter                     ← Executes SET search_path on the active
  │   (runs after JWT filter)       database connection
  │
  ▼
Spring Security Authorization    ← Enforces permitAll / authenticated rules
  │
  ▼
Controller
```

### JWT Claims
| Claim | Description |
|---|---|
| `sub` | Admin email |
| `userId` | UUID of the admin/user |
| `role` | e.g. `ROLE_PLATFORM_ADMIN`, `ROLE_ADMIN` |
| `tenant` | Schema identifier (`system` or tenant slug) |
| `impersonatedBy` | *(Impersonation tokens only)* Initiating admin UUID |
| `impersonationSessionId` | *(Impersonation tokens only)* Session UUID |

**Token TTLs**
- Standard token: **30 minutes**
- Impersonation token: **2 hours**

### Public Endpoints
```
POST /api/v1/platform/auth/login
POST /api/v1/platform/auth/mfa/enroll
POST /api/v1/platform/tenants/{slug}/impersonate
```

---

## Request Lifecycle

```
1. Client sends:  Authorization: Bearer <jwt>

2. JwtAuthenticationFilter
   ├── Parses JWT → extracts email, role, tenant
   ├── Sets TenantContext.setTenant(tenant)      ← ThreadLocal
   └── Sets SecurityContext authentication

3. TenantFilter
   └── Executes:  SET search_path TO tenant_<slug>
                  (or SET search_path TO system for platform admins)

4. Controller/Service executes queries
   └── JPA/JDBC targets the correct schema transparently

5. After response:  TenantContext.clear()         ← Thread cleanup
```

---

## Tenant Provisioning Flow

When `POST /api/v1/platform/tenants` is called:

```
TenantService.createTenant(slug, companyName, adminEmail)
  │
  ├── 1. Check slug uniqueness  →  throw if duplicate
  ├── 2. Save Tenant entity     →  system.tenants
  ├── 3. TenantProvisioningService.provisionTenantSchema(slug)
  │       ├── CREATE SCHEMA IF NOT EXISTS tenant_<slug>
  │       └── Flyway.migrate()  →  runs db/migration/tenant/*.sql
  └── 4. TenantUserBootstrapService.createSuperAdmin(slug, email)
          └── INSERT INTO tenant_<slug>.users (id, email, role='super_admin', ...)
```

---

## Local Development Setup

### Prerequisites
- Java 21
- Maven 3.9+
- PostgreSQL 14+

### 1. Create the Database
```sql
CREATE DATABASE aisafe;
```

### 2. Configure `application.properties`
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/aisafe
spring.datasource.username=postgres
spring.datasource.password=root
```

### 3. Run the Application
```bash
./mvnw spring-boot:run
```

Flyway will automatically create the `system` schema and run all system migrations on startup.

### 4. Seed a Platform Admin (manual SQL)
```sql
INSERT INTO system.platform_admins (id, email, password_hash, full_name)
VALUES (
  gen_random_uuid(),
  'admin@icentric.io',
  '$2a$10$...',   -- BCrypt hash of your password
  'Platform Admin'
);
```

### 5. Enroll MFA (optional)
```bash
curl -X POST "http://localhost:8080/api/v1/platform/auth/mfa/enroll?email=admin@icentric.io" \
  --output qr.png
```
Open `qr.png` and scan with your authenticator app.

### 6. Login
```bash
curl -X POST http://localhost:8080/api/v1/platform/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@icentric.io","password":"yourpassword","mfaCode":"123456"}'
```

---

## Known Issues / TODOs

| # | Issue | Recommendation |
|---|---|---|
| 1 | JWT secret is hardcoded (`mysupersecretkey...`) | Move to environment variable / Vault |
| 2 | Impersonation endpoint is publicly accessible (no auth) | Require `ROLE_PLATFORM_ADMIN` token |
| 3 | Auth errors throw `RuntimeException` → 500 responses | Return `401 Unauthorized` with proper error body |
| 4 | `TenantFilter` doesn't close/release the database connection/statement | Use try-with-resources or Spring's `DataSourceUtils.releaseConnection` |
| 5 | No input validation (`@Valid`) on request bodies | Add Bean Validation (`@NotBlank`, `@NotNull`, etc.) |
| 6 | `impersonate` endpoint resolves `platformAdminId` via `getAuthentication().getPrincipal()` but the principal is the email string, not UUID | Fix principal extraction or use a custom `UserDetails` |
| 7 | No test coverage beyond the default empty test class | Write unit tests for services and integration tests for controllers |
