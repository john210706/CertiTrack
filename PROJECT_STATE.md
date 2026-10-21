# CertiTrack Project State

## Status

**Phases 1–9 are complete.** The requested college project is implemented,
tested, documented, packaged, and ready to demonstrate.

## Implemented scope

- Phase 1: Java 17/Spring Boot foundation, MySQL configuration, test-only H2,
  Maven wrapper, and executable packaging.
- Phase 2: user roles, BCrypt authentication, session rotation/logout, CSRF,
  admin account management, disabled-session enforcement, and opt-in first-admin
  provisioning.
- Phase 3: transactional issuance, immutable database-key certificate IDs,
  student/date validation, scoped lists/details, and student lookup.
- Phase 4: public verification, strict ID validation, safe public DTOs, complete
  attempt logging, effective expiration, and consistent JSON errors.
- Phase 5: authorized permanent revocation, duplicate/concurrency protection,
  reusable expiration processing, and configurable daily scheduling.
- Phase 6: responsive public and role-specific HTML/CSS/JavaScript pages,
  dashboards, safe DOM rendering, and shared Fetch/session handling.
- Phase 7: database-backed scoped search, effective status/type/year filters,
  reports, paginated audit history, and formula-safe CSV export.
- Phase 8: end-to-end browser/API review, security headers, bounded pagination,
  CSV batch-memory cleanup, dead-query cleanup, frontend form fix, and a
  server-side guard that prevents an administrator disabling their own account.
- Phase 9: idempotent opt-in demo users/certificates, final README and
  presentation guide, complete tests, and final executable JAR.

Optional bulk CSV import was intentionally omitted. The specification marks it
optional; export covers the required file-I/O demonstration without adding an
unsafe or unnecessary ingestion surface.

## Persistence and security

MySQL database `certitrack` contains `app_users`, `certificates`, and
`verification_logs`. Emails and public certificate IDs are unique. Certificates
retain issuer/student foreign keys, lifecycle timestamps/reason, and optimistic
versioning. No application path deletes certificates.

Security uses Spring Security sessions, BCrypt, CSRF, role checks at protected
routes, secure cookie defaults, a content security policy, frame denial,
content-type protection, safe DTO allowlists, normalized/validated input, and
centralized errors without traces. Search wildcards are escaped and JPA
parameters are bound. CSV output neutralizes spreadsheet formulas.

## Verification results

Final automated run on JDK 17.0.20.1 and Maven 3.9.11:

```text
Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Coverage includes authentication/session/CSRF behavior, roles, account safety,
issuance and concurrent IDs, scoped access, lifecycle boundaries, concurrent
revocation/expiration, public data allowlists and audit records, dashboards,
search/filter escaping, reports, CSV safety, demo-seed idempotence, first-admin
bootstrap, and a real HTTP/scheduler lifecycle.

The shared JavaScript passed Node syntax checking. The responsive UI was also
walked through in a browser: landing, login, admin creation, issuance, search,
and certificate details. A final live database smoke test started CertiTrack
against temporary MariaDB 11.8 using the MySQL dialect, seeded all three roles
and lifecycle states, served the UI, and returned a valid public verification.
The project's declared production target remains MySQL 8.

Final package command:

```sh
./mvnw clean package
```

Workspace-local fallback used where the host Java/Maven setup was unsuitable:

```sh
JAVA_HOME="$PWD/.build-tools/jdk17" \
  .build-tools/apache-maven-3.9.11/bin/mvn \
  -Dmaven.repo.local=.build-tools/m2 clean package
```

Output: `target/certitrack-0.0.1-SNAPSHOT.jar`.

## Manual demonstration

1. Configure an empty MySQL 8 `certitrack` database.
2. Start with the `demo` profile and open `http://localhost:8080`.
3. Verify `CERT-JAVA-<current-year>-00001` publicly.
4. Sign in as each documented demo account to show role-specific dashboards.
5. As admin, create/disable a user, filter certificates, inspect verification
   history, and export CSV.
6. As issuer, issue and revoke a certificate; verify the changed public status.
7. As student, show that only the student's own certificates are visible.

The polished sequence and talking points are in `PRESENTATION_GUIDE.md`.

## Known constraints and deferred extras

- Production should replace Hibernate `update` with managed migrations and
  `validate`, terminate HTTPS in deployment, and set `SESSION_COOKIE_SECURE=true`.
- Public ID numeric suffixes support database keys through 99,999,999; issuance
  fails safely beyond that explicit limit.
- The demo profile uses fixed local credentials and must stay disabled outside
  presentations.
- QR codes, cryptographic certificate files, email delivery, containers,
  frontend frameworks, and distributed infrastructure were outside the project
  scope and were not added.

No known failures remain in the implemented scope. There is no next phase in
the supplied roadmap.
