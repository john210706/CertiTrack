# CertiTrack Project Specification

## 1. Project identity

**Name:** CertiTrack

**Full title:** CertiTrack: A Java-Based Digital Certificate Issuance,
Verification and Lifecycle Management System Using Spring Boot and Hibernate

CertiTrack is a reasonably sized monolithic college Java web application. It
manages certificate records throughout issuance, verification, expiry, and
revocation while demonstrating core Java, database, web, and Spring concepts.

## 2. Scope and goals

The completed system will:

1. Create and manage users.
2. Register students and issuers.
3. Issue certificates to students.
4. Generate unique, human-readable certificate IDs.
5. Persist users, certificates, and verification history in PostgreSQL.
6. Let students sign in and view only their own certificates.
7. Let the public verify a certificate without signing in.
8. Report `VALID`, `EXPIRED`, `REVOKED`, `NOT_FOUND`, or `INVALID_FORMAT` as
   appropriate.
9. Let authorized administrators and issuers revoke certificates.
10. Record every public verification attempt.
11. Mark eligible certificates expired through a scheduled background process.
12. Provide role-appropriate dashboards, search, filters, and reports.
13. Demonstrate appropriate Java syllabus concepts without artificial or
    unnecessary patterns.

## 3. Explicit exclusions

The project must not include:

- QR codes or QR-code verification
- Certificate hashing, SHA-256 fingerprints, or tamper-detection hashes
- Blockchain or cryptographic certificate-signing infrastructure
- Microservices, Spring Cloud, or distributed architecture
- Kafka, RabbitMQ, or Redis
- Docker unless explicitly requested in a later phase
- Kubernetes
- AI or machine learning
- React, Angular, Vue, or mobile applications

BCrypt password hashing is required and is not part of the certificate-hashing
exclusion.

## 4. Technology stack

### Backend

- Java 17
- Maven
- Spring Boot 3.x
- Spring Web and Spring MVC
- Spring Data JPA and Hibernate
- PostgreSQL database named `certitrack`
- Jakarta Validation
- `HttpSession` session management
- BCrypt password storage
- SLF4J/Spring logging
- Spring Scheduling

### Testing

- JUnit 5
- Mockito where appropriate
- Spring Boot Test
- H2 for automated tests when a database is needed

### Frontend

- HTML5 and CSS3
- Vanilla JavaScript
- Fetch API for asynchronous requests
- Static pages served by the same Spring Boot application

## 5. Architecture

Use a clean layered monolith rooted at `com.certitrack`:

```text
com.certitrack
|-- config
|-- controller
|-- dto
|   |-- request
|   `-- response
|-- exception
|-- interceptor
|-- model
|   |-- entity
|   `-- enums
|-- repository
|-- scheduler
|-- service
|   `-- impl
|-- util
`-- CertiTrackApplication.java
```

Static frontend resources will use this layout:

```text
src/main/resources/static/
|-- css/
|-- js/
|-- admin/
|-- issuer/
|-- student/
|-- index.html
|-- login.html
`-- verify.html
```

Responsibilities must remain separated:

- Controllers handle HTTP requests and responses only.
- Services contain business rules and transaction boundaries.
- Repositories perform database access.
- DTOs define safe external request and response shapes.
- Entities represent persistence state and are not returned directly by REST
  controllers.
- Utilities contain focused, reusable validation and generation helpers.
- Authorization should be centralized with an interceptor or similarly
  maintainable mechanism rather than repeated in every controller.

Use constructor injection, final dependencies where appropriate, service
interfaces, DTO mapping, and small focused methods. Avoid Lombok unless a later
decision provides a strong reason to use it.

## 6. Roles and authorization

### Administrator (`ADMIN`)

May sign in, view the admin dashboard, create/view/enable/disable users, create
student and issuer accounts, issue certificates, view/search/filter all
certificates, view details, revoke any certificate, view verification logs, and
view reports.

### Issuer (`ISSUER`)

May sign in, view the issuer dashboard, issue certificates, view/search/filter
certificates issued by that same issuer, view their details, and revoke only
certificates that issuer issued. Issuers may not perform administrator-only user
management.

### Student (`STUDENT`)

May sign in, view the student dashboard, and view details and status for only
their own certificates. A student must never see another student's private
account data and cannot revoke certificates.

### Public verifier

May submit a certificate ID and receive the restricted public verification
result without an account. Public users cannot issue or revoke certificates.

## 7. Domain model

### User

Suggested fields:

- `Long id`
- `String fullName`
- `String email`
- `String password`
- `Role role`
- `String registrationNumber`
- `boolean enabled`
- `LocalDateTime createdAt`

`Role` contains `ADMIN`, `ISSUER`, and `STUDENT`.

Rules:

- Email addresses are unique.
- Registration number is primarily required for students.
- Passwords are stored only as BCrypt hashes.
- Passwords and hashes are never returned or logged.
- Disabled users cannot sign in.

### Certificate

Suggested fields:

- `Long id`
- `String certificateId`
- `User student`
- `User issuedBy`
- `String programCode`
- `String programName`
- `String certificateType`
- `String organizationName`
- `LocalDate issueDate`
- `LocalDate expiryDate` (nullable)
- `CertificateStatus status`
- `String revocationReason`
- `LocalDateTime revokedAt`
- `LocalDateTime createdAt`
- `LocalDateTime updatedAt`

`CertificateStatus` contains `VALID`, `EXPIRED`, and `REVOKED`.

Rules:

- Public certificate ID is unique, application-generated, and not manually
  editable.
- A null expiry date means the certificate does not expire.
- Expiry date cannot precede issue date.
- A newly issued certificate normally begins as `VALID`.
- Revocation is permanent; a revoked certificate must never later become
  expired.
- Issued certificates generally are not physically deleted.

### Verification log

Suggested fields:

- `Long id`
- `String searchedCertificateId`
- `Long matchedCertificateDatabaseId` (nullable)
- `VerificationResult result`
- `LocalDateTime verifiedAt`

`VerificationResult` contains `VALID`, `EXPIRED`, `REVOKED`, `NOT_FOUND`, and
`INVALID_FORMAT`. The model must support logging unsuccessful searches without a
certificate foreign key and must not record passwords or sensitive
authentication data.

### Relationships

- A student may own many certificates.
- An administrator or issuer may issue many certificates.
- Each certificate belongs to one student and has one issuing user whose role is
  `ADMIN` or `ISSUER`.
- A verification log may optionally refer internally to a matched certificate.
- Prefer unidirectional `@ManyToOne` relationships with appropriate join columns
  and fetch choices. Avoid unnecessary bidirectional relationships and prevent
  JSON recursion by using DTOs.

## 8. Certificate ID rules

Format:

```text
CERT-{PROGRAM_CODE}-{YEAR}-{SEQUENCE}
```

Examples:

```text
CERT-JAVA-2026-00001
CERT-AI-2026-00002
CERT-CLOUD-2027-00125
```

Validation regex:

```regex
^CERT-[A-Z0-9]{2,12}-[0-9]{4}-[0-9]{5,8}$
```

Program codes contain approximately 2-12 uppercase letters or digits. ID
generation must be concurrency-safe and based on the database-generated numeric
certificate primary key:

1. Save the certificate to obtain its numeric primary key.
2. Combine the normalized program code, issue year, and zero-padded primary key.
3. Save the generated public ID in the same transactional operation.

Do not use an in-memory global counter.

## 9. Certificate verification

The principal public endpoint is planned as:

```http
GET /api/public/verify/{certificateId}
```

Processing must distinguish these outcomes:

- `INVALID_FORMAT`: input fails the certificate ID regex.
- `NOT_FOUND`: well-formed ID has no matching certificate.
- `VALID`, `EXPIRED`, or `REVOKED`: matching certificate's effective state.

Every attempt is eventually logged. An existing-certificate response may expose
only:

- Certificate ID
- Holder name
- Program name
- Certificate type
- Organization name
- Issue date
- Expiry date or “No Expiry”
- Status

It must not expose email, password/hash, internal user ID, registration number,
issuer credentials, or other private account details.

## 10. Certificate lifecycle

### Revocation

- Administrator may revoke any certificate.
- Issuer may revoke only a certificate they issued.
- Student and public users cannot revoke.
- A nonblank reason and timestamp are required.
- Status becomes `REVOKED`.
- An already revoked certificate cannot be revoked again.
- Revoked certificates remain revoked permanently.

### Expiration

A `VALID` certificate with an expiry date changes to `EXPIRED` when the current
date is later than its expiry date. Null expiry means no expiration. Expiration
processing must never change a revoked certificate.

The transition logic belongs in a reusable, independently testable service
method. A Spring scheduled task will invoke it; the normal schedule may run once
per day while development/test configuration may run more frequently.

## 11. Authentication and sessions

Authentication is session-based, not JWT-based. Planned endpoints:

```http
POST /api/auth/login
GET  /api/auth/me
POST /api/auth/logout
```

On successful login, create an `HttpSession` containing only the necessary user
identity and role information. The browser will use the session cookie. Reject
invalid credentials and disabled users. Never store plaintext passwords, log
raw passwords, or return password hashes.

## 12. Planned REST API

Endpoint names may be adjusted only for a strong architectural reason and must
remain consistent.

```http
# Authentication
POST  /api/auth/login
POST  /api/auth/logout
GET   /api/auth/me

# Administrator user management
GET   /api/admin/users
POST  /api/admin/users
GET   /api/admin/users/{id}
PATCH /api/admin/users/{id}/enabled

# Student lookup for issuance
GET   /api/students
GET   /api/students/{id}

# Certificates
POST  /api/certificates
GET   /api/certificates
GET   /api/certificates/{certificateId}
PUT   /api/certificates/{certificateId}          # only if needed
POST  /api/certificates/{certificateId}/revoke
GET   /api/student/certificates

# Public verification
GET   /api/public/verify/{certificateId}

# Administration and reporting
GET   /api/admin/reports/summary
GET   /api/admin/verification-logs
GET   /api/admin/certificates/export             # later CSV phase
POST  /api/admin/certificates/import             # optional later CSV import
```

## 13. Validation and errors

Use Jakarta constraints such as `@NotBlank`, `@NotNull`, `@Email`, `@Size`, and
`@Pattern`, plus service-layer validation for business rules.

Expected application-specific exceptions include:

- `CertificateNotFoundException`
- `InvalidCertificateIdException`
- `CertificateAlreadyRevokedException`
- `InvalidExpiryDateException`
- `UnauthorizedCertificateActionException`
- `UserNotFoundException`
- `DuplicateEmailException`
- `InvalidCredentialsException`
- `DisabledUserException`

A centralized `@RestControllerAdvice` must produce consistent JSON errors and
must not expose stack traces. Target shape:

```json
{
  "timestamp": "...",
  "status": 404,
  "error": "Not Found",
  "message": "Certificate not found",
  "path": "/api/..."
}
```

## 14. Transactions and persistence

Use `@Transactional` where atomicity matters, especially certificate issuance,
revocation, expiration batches, and later bulk CSV import. Search and filtering
should use repository queries/specifications rather than loading all records
into application memory.

Production/demo persistence uses PostgreSQL. Tests may use H2. Configuration will
read environment variables with safe local defaults, for example:

```properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/certitrack}
spring.datasource.username=${DB_USERNAME:certitrack}
spring.datasource.password=${DB_PASSWORD:}
```

No real credentials may be committed. Development should use an appropriate
Hibernate `ddl-auto` mode.

## 15. Frontend and dashboards

Planned pages:

```text
Public:  /, verify.html, login.html
Admin:   admin/dashboard.html, admin/users.html,
         admin/certificates.html, admin/issue-certificate.html,
         admin/reports.html
Issuer:  issuer/dashboard.html, issuer/certificates.html,
         issuer/issue-certificate.html
Student: student/dashboard.html, student/certificates.html
```

Use shared CSS, shared JavaScript utilities, and reusable navigation where
practical. Fetch-based pages should show friendly errors rather than raw JSON.
Business rules remain on the server, not in JavaScript.

Dashboard measures:

- Admin: total/valid/expired/revoked certificates, total students, total
  issuers, and today's verification attempts.
- Issuer: total issued plus valid/expired/revoked counts for that issuer.
- Student: total plus valid/expired/revoked counts for that student.

## 16. Search, filters, reports, and CSV

Certificate management will support searching by certificate ID, student name,
and program name, with filters for status, certificate type, and issue year.

Later CSV export fields may include certificate ID, student name, program code,
program name, certificate type, organization, issue date, expiry date, and
status. Optional bulk import must validate every row, report row-level errors,
never import passwords, never silently ignore malformed data, and never
overwrite certificate IDs.

## 17. Java concepts demonstrated

- Classes and objects through entities, DTOs, services, and controllers
- Interfaces, overriding, and polymorphism through service contracts and Spring
  implementations
- JPA no-argument constructors and useful convenience constructors
- String processing and regex for certificate IDs
- Collections and generics, including a reusable response DTO such as
  `ApiResponse<T>` where useful
- Static utility methods where appropriate
- Custom exceptions and centralized handling
- `LocalDate` and `LocalDateTime`
- JDBC via Spring Data JPA/Hibernate and the PostgreSQL driver
- ORM relationships and repositories
- Background processing through the expiration scheduler
- File I/O during the later CSV phase
- REST, MVC, and `HttpSession`

Synchronization must not be added merely for demonstration; it requires a real
concurrency use case.

## 18. Demo data

A later, optional, configurable development-only seeder may create clearly
documented demo accounts such as:

- `admin@certitrack.local` (`ADMIN`)
- `issuer@certitrack.local` (`ISSUER`)
- `student@certitrack.local` (`STUDENT`)

Any demo passwords are local demonstration credentials and must be stored with
BCrypt. Seeding must be possible to disable.

## 19. Documentation requirements

- `README.md`: purpose, features, stack, prerequisites, PostgreSQL setup,
  configuration, run instructions, demo accounts, API overview, and project
  structure as those become available.
- `PROJECT_SPEC.md`: this stable architecture and requirements reference.
- `PROJECT_STATE.md`: phase status, implemented features, added modules,
  database changes, tests, manual test steps, known issues, deferred work, and
  next phase; update it after every phase.

## 20. Phase roadmap

1. Project foundation and database configuration
2. User model, roles, authentication, and session management
3. Certificate domain, issuance, and certificate ID generation
4. Public verification, regex validation, verification logs, and exception
   handling
5. Certificate revocation and automatic expiry processing
6. Frontend pages and dashboards
7. Search, filtering, reports, and CSV file I/O
8. Comprehensive testing, validation, security review, and cleanup
9. Final documentation, demo data, packaging, and presentation readiness

## 21. Mandatory incremental workflow

No phase may be implemented until explicitly requested. For each request to
implement a phase:

1. Read `PROJECT_SPEC.md` and `PROJECT_STATE.md`.
2. Inspect the existing repository and preserve working earlier-phase code.
3. Implement only the requested phase; do not begin future phases.
4. Add or update tests for that phase.
5. Run Maven tests and, where practical, Maven compilation/package.
6. Fix failures caused by the phase changes.
7. Update `PROJECT_STATE.md` completely.
8. Provide a concise completion report and stop.

## 22. Implementation decisions recorded during Phases 1–9

On 2026-10-24, the user authorized replacing MySQL with PostgreSQL. PostgreSQL
is now the application database, with H2 PostgreSQL mode for automated tests.
This change configures a fresh database; transfer of existing data is separate.

The user explicitly authorized Phases 1–5 together on 2026-09-27. The stop point
for that request is Phase 5; Phases 6–9 remain separately gated.

- Standard Spring Security supplies session persistence, BCrypt, endpoint role
  rules, logout, and CSRF protection. `GET /api/auth/csrf` supports JSON clients;
  all mutating requests require its token. A separate interceptor checks
  disabled accounts on protected API requests.
- A `bootstrap-admin` profile provisions the first administrator from explicit
  environment variables, without default/demo credentials.
- Public verification uses HTTP 200 for all five business outcomes; errors
  in authenticated operations retain their appropriate HTTP status codes.
- Paginated lists default to 20 rows, cap at 100, and permit only ID sorting.
- `APP_TIME_ZONE` defaults to Asia/Kolkata and governs application timestamps,
  date evaluation, and scheduling. Expiry day itself remains valid.
- Both public and private certificate responses calculate effective expiry
  before the scheduler persists changes. Revocation of expired records is
  permitted and remains permanent.
- Issuance is one transaction, including temporary null public ID and subsequent
  generation from the database key. The eight-digit sequence ceiling is
  explicit, and exceeding it aborts issuance.
- The user subsequently authorized Phases 6–9 together. The UI remains a
  same-origin vanilla frontend so no JavaScript framework or duplicate API
  client layer is required.
- Dashboard, search, filtering, and CSV export apply role scope in database
  queries. CSV import remains omitted because it is optional and would add a
  materially larger validation surface without being needed for the project.
- Administrators cannot disable their own account; this rule is enforced by the
  service, not only by the browser UI.
- The `demo` profile is the explicit off-by-default seeding switch. It creates
  idempotent BCrypt accounts and representative valid, expired, and revoked
  records. Fixed demo credentials are documented as local-only.
- Phase 8/9 verification includes 34 automated tests, a real HTTP/scheduler
  test, browser walkthrough, JavaScript syntax check, and a live relational
  database smoke test. Final presentation steps live in `PRESENTATION_GUIDE.md`.
