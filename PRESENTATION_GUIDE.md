# CertiTrack Presentation Guide

## One-sentence pitch

CertiTrack gives a college one secure place to issue, manage, audit, and
publicly verify digital certificate records throughout their lifecycle.

## Suggested 6-minute demonstration

1. **Problem and solution (30 seconds).** Explain that paper/PDF certificates
   are difficult to validate and lifecycle changes are hard to track.
2. **Public verification (45 seconds).** Open the landing page, verify the demo
   valid certificate, then try a malformed ID. Point out the safe public fields
   and that every attempt is audited.
3. **Administrator view (90 seconds).** Sign in as the demo administrator. Show
   dashboard counts, user creation/enabling, certificate filters, verification
   history, and CSV export.
4. **Issuer workflow (90 seconds).** Sign in as the demo issuer, select the demo
   student, issue a certificate, find it in the issuer's scoped list, and revoke
   it with a reason.
5. **Student privacy (45 seconds).** Sign in as the demo student and show that
   only that student's certificates are visible.
6. **Engineering summary (60 seconds).** Mention Spring Boot's layered design,
   MySQL/JPA persistence, BCrypt/session/CSRF security, transactional issuance,
   immutable IDs, scheduled expiry, audit logging, and automated tests.

Demo accounts and startup instructions are in `README.md`. The seeded valid ID
is normally `CERT-JAVA-<current-year>-00001` on a new database; copy the exact
ID from the admin certificate list before presenting.

## Architecture talking point

```text
Browser UI / Public verifier
          |
Spring MVC controllers + Spring Security
          |
Transactional services and lifecycle rules
          |
Spring Data JPA / Hibernate
          |
        MySQL
```

## Likely viva questions

- **Why sessions instead of JWT?** It is a same-origin monolith; server-side
  sessions are simpler, revocable, and pair naturally with CSRF protection.
- **Why derive the certificate ID from the database key?** The database already
  provides concurrency-safe uniqueness, avoiding a fragile custom counter.
- **When does a certificate expire?** The day after its expiry date; it remains
  valid through the stated date.
- **Can revocation be reversed?** No. It is intentionally permanent, timestamped,
  and protected from duplicate/concurrent writes.
- **What is public?** Only the holder name and certificate facts. Account IDs,
  email, registration number, password hash, and private revocation reason are
  never returned by public verification.
- **How is it tested?** Thirty-four automated checks cover service, database,
  security, HTTP, concurrency, scheduling, search, CSV, and demo startup flows.
