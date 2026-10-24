# CertiTrack

CertiTrack is a complete Java web application for issuing, managing, revoking,
searching, exporting, and publicly verifying educational certificates. It is a
single Spring Boot application with a responsive vanilla HTML/CSS/JavaScript UI.

## Features

- Administrator, issuer, and student roles with BCrypt passwords and server-side sessions
- Admin account management; administrators cannot disable their own account
- Transactional certificate issuance with immutable IDs such as `CERT-JAVA-2026-00142`
- Scoped dashboards, certificate lists, details, search, and filters
- Public verification with `VALID`, `EXPIRED`, `REVOKED`, `NOT_FOUND`, and `INVALID_FORMAT` outcomes
- Permanent revocation and automatic daily expiration
- Verification audit history and safe CSV export
- CSRF protection, role-based authorization, CSP/security headers, validation, and safe JSON errors
- Optional idempotent demo-data profile and executable JAR packaging

Stack: Java 17, Spring Boot 3.5, Spring MVC, Spring Security, Spring Data
JPA/Hibernate, Jakarta Validation, PostgreSQL, and Maven. Tests use H2 in PostgreSQL
mode and include a real embedded HTTP-server flow.

## Quick demo

Create an empty PostgreSQL database as shown below, configure `DB_USERNAME` and
`DB_PASSWORD`, then run:

```sh
./run.sh
```

`run.sh` loads the local `.env` file and starts the selected Spring profile.
The committed `.env.example` documents the settings; `.env` is ignored by Git
so local database credentials are not committed.

Open `http://localhost:8080`. The `demo` profile creates these local accounts
only when missing, with the shared password `Demo-password-42`:

| Role | Email |
|---|---|
| Administrator | `admin@certitrack.local` |
| Issuer | `issuer@certitrack.local` |
| Student | `student@certitrack.local` |

It also creates one valid, one expired, and one revoked certificate. The demo
profile is disabled by default; omit `demo` for normal use. These credentials
are demonstration-only and must not be used in a deployed system.

## Prerequisites and database

Install JDK 17 and PostgreSQL. The Maven wrapper downloads Maven 3.9.11 on first
use. Connect as a PostgreSQL administrator (for example, `sudo -u postgres psql`
on Linux), then create an application role and its database:

```sql
CREATE ROLE certitrack LOGIN PASSWORD 'replace-with-a-local-password';
CREATE DATABASE certitrack OWNER certitrack;
\q
```

Configure the process environment; never commit real credentials:

```sh
export DB_URL='jdbc:postgresql://localhost:5432/certitrack'
export DB_USERNAME='certitrack'
export DB_PASSWORD='replace-with-a-local-password'
```

The application role owns the database so Hibernate can create tables in its
default schema. This setup creates a fresh database; existing MySQL records are
not transferred automatically. Keep any existing MySQL database until its data
has been separately migrated and verified.

Other settings:

| Variable | Default | Purpose |
|---|---|---|
| `DB_DDL_AUTO` | `update` | Development schema handling; use managed migrations/`validate` in production |
| `APP_TIME_ZONE` | `Asia/Kolkata` | Date, timestamp, and scheduler zone |
| `EXPIRY_ENABLED` | `true` | Enables scheduled expiration |
| `EXPIRY_CRON` | `0 0 0 * * *` | Spring cron expression for expiration |
| `SESSION_COOKIE_SECURE` | `false` | Set `true` when serving over HTTPS |

## Build, test, and run

```sh
./mvnw clean test
./mvnw clean package
java -jar target/certitrack-0.0.1-SNAPSHOT.jar
```

Use `mvnw.cmd` on Windows. The test suite requires permission to bind one
temporary localhost port; it does not require an external database. The
packaged application contains the PostgreSQL driver, not the test-only H2 driver.

For a non-demo first startup, provision exactly one administrator from explicit
environment values:

```sh
export BOOTSTRAP_ADMIN_NAME='System Administrator'
export BOOTSTRAP_ADMIN_EMAIL='admin@example.edu'
export BOOTSTRAP_ADMIN_PASSWORD='choose-a-strong-password'
./mvnw spring-boot:run -Dspring-boot.run.profiles=bootstrap-admin
```

This profile creates an administrator only if none exists. Stop the app, unset
the three values, and restart normally.

## UI and API overview

Public pages are `/`, `/verify.html`, and `/login.html`. Each signed-in role is
sent to its own dashboard under `/admin`, `/issuer`, or `/student`.

Key endpoints:

| Method and path | Access | Purpose |
|---|---|---|
| `GET /api/public/verify/{certificateId}` | Public | Verify and audit an ID |
| `GET /api/auth/csrf` | Public | Obtain a CSRF token |
| `POST /api/auth/login`, `POST /api/auth/logout` | Public/session | Start or end a session |
| `GET /api/auth/me`, `GET /api/dashboard` | Signed in | Current account and scoped summary |
| `GET/POST /api/admin/users` | Admin | List or create users |
| `PATCH /api/admin/users/{id}/enabled` | Admin | Enable or disable another account |
| `GET/POST /api/certificates` | Scoped / admin or issuer | List or issue certificates |
| `GET /api/certificates/{certificateId}` | Scoped | View private details |
| `POST /api/certificates/{certificateId}/revoke` | Admin or issuing issuer | Permanently revoke |
| `GET /api/admin/verification-logs` | Admin | View verification history |
| `GET /api/admin/certificates/export` | Admin | Download filtered CSV |

Certificate lists and CSV export accept `search`, `status`, `certificateType`,
and `issueYear`; lists also accept `page`, `size`, and safe ID sorting. Search
covers certificate ID, student name, and program name. CSV cells are correctly
quoted and spreadsheet-formula prefixes are neutralized. Bulk CSV import was
optional in the specification and is intentionally not included.

All state-changing requests use the session cookie plus an `X-CSRF-TOKEN`
header. Browser pages handle this automatically. API clients should fetch the
token before login and fetch a fresh one after login because the session ID and
token rotate. Dates are ISO `YYYY-MM-DD`; a null expiry means no expiry.

## Lifecycle rules

A certificate remains valid through its expiry date and expires the following
day. Responses calculate that effective state immediately; the scheduler later
persists it. Revocation always wins and cannot be undone. Public responses never
expose email, account IDs, registration numbers, password hashes, or private
revocation reasons. Every public verification attempt is logged.

## Project structure

```text
src/main/java/com/certitrack/
  config/       Security, clock, scheduling, bootstrap and demo data
  controller/   REST endpoints
  dto/          Validated requests and safe responses
  exception/    Domain errors and centralized handling
  interceptor/  Disabled-session enforcement
  model/        JPA entities and enums
  repository/   Database access and specifications
  scheduler/    Expiration trigger
  service/      Transactional business logic
  util/         Certificate IDs, CSV, and safe pagination
src/main/resources/static/  Responsive role-based UI
src/test/                   Unit and integration tests
```

See [PROJECT_SPEC.md](PROJECT_SPEC.md), [PROJECT_STATE.md](PROJECT_STATE.md),
and [PRESENTATION_GUIDE.md](PRESENTATION_GUIDE.md) for the requirements,
completion record, and a short demonstration script.
# CertiTrack
