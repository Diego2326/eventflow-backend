# API implementation and verification

**Verified 2026-10-30.** Supabase was not contacted or modified.

## Implemented

- Google sign-in verifies the signed ID token using Google's JWK set, expiry, issuer, configured audience and verified-email claim. Verified identities create JWT/refresh sessions, link an existing account by verified email, and report an incomplete profile when the user must add a phone number.
- Account verification, password reset and email change deliver one-use, expiring links through Spring Mail/SMTP configuration. Web and `eventflow://` links are included. Protected profiles reject token exposure and require mail host, sender address and mail-provider configuration.
- Invite/ticket QR check-in and check-out remain staff-authorized; guests receive 403. V22 adds push-device persistence without changing V1–V4 or earlier migration files.
- Expo device registration/list/revocation is owner-scoped and never returns the device token. Notifications dispatch after commit through server-side Expo configuration in batches of 100.
- STOMP `/ws` authenticates a JWT and persisted session at connect, checks event/module membership at subscribe/send, and rechecks session validity on each operation. Existing HTTP chat routes remain.
- Search ignores opaque UUID references in record payloads so random IDs do not become false textual matches.
- API errors use a stable JSON error envelope; unexpected failures no longer print exception stack traces.

## Checks performed

- `.\gradlew.bat clean build --console=plain` with JDK 25: **BUILD SUCCESSFUL**, 43 tests, 0 failures, 0 skipped.
- New local PostgreSQL cluster restored from the SHA-256-verified backup; migration line `migration-safe` validated V1–V4 and applied V5–V22, one version at a time. All 22 checksums passed; V7 installed `btree_gist 1.8`; safe V8/V9 created no demo users or ADMIN rows.
- The two source users retained their original fingerprint, remain unverified and have no automatically assigned roles. Runtime role had no Flyway-history access or DDL, while Hibernate `validate` passed.
- Local hardening and `validate-safe-deployment.sql` passed against the isolated database. PostgreSQL HTTP/integration suite: **43 tests, 0 failures, 0 errors, 0 skipped**. The temporary server was stopped.
- Actual HTTP tests cover registration/login/refresh/profile, Google account linking through a test verifier, password/email flows, event/RSVP/agenda/capacity, invite/ticket QR authorization, Expo delivery through a local HTTP stub, WebSocket/STOMP including revoked-session rejection, and Swagger/OpenAPI. A separate JWK test covers signed/expired/wrong-audience/wrong-issuer/unverified-email Google tokens. Mail delivery uses a mocked Spring mail provider; live SMTP was not contacted.

Ignored local PostgreSQL evidence and XML are under `backups/migration-rehearsal-1793416800366/` (`result.txt`, `postgresql-test-summary.txt`, and `postgresql-test-results/`).

## Integration boundary

The React Native/Expo and web client repositories were not available, so no client-side integration is claimed. Real Google OAuth client IDs, SMTP credentials, Expo project configuration/access token, production HTTPS origins and mobile deep-link registrations must be supplied by deployment operators. The Expo request was verified against a local stub, not a live Expo project. Supabase still has not received V5–V22, V22 is only a new local migration, and no remote permissions or data were changed.

Client request/response contracts and platform base URLs are in [FRONTEND_API_HANDOFF.md](FRONTEND_API_HANDOFF.md).

## REST additions (2026-10-30)

V23 adds per-user notification read receipts. It is additive and duplicated in the safe migration directory; existing versions are unchanged. HTTP integration coverage now checks session listing, owner event permissions, personal reservation listing and notification detail/read/read-all. V23 has only been applied by local test databases; Supabase has not been changed.

Additional REST coverage: conversation list/detail derives thread IDs from reservation or participant IDs and filters through the existing per-event authorization. Admin event/audit reads are ADMIN-only. V23 migration is additive; do not run it remotely as part of this change.

## Scope still missing or requiring a product contract

`EventFlow_Handoff_Backend` was not present in the workspace; the audit used the available `docs/frontend/*` contracts and the controllers. There is no persisted event-template model/API, no separate privacy-control model/API (only profile and notification preferences), and module-specific payload schemas remain generic for most DERCAS modules. The HTTP conversation endpoints expose authorized threads, but per-message read receipts are not implemented. These were not inferred from undocumented behavior. The complete route list is in `REST_ENDPOINT_CATALOG.md`; the OpenAPI document is the executable contract.

## Addendum: module audit and admin paging

The older scope notes above describe the previous API revision. Event-template persistence/application, profile privacy, and per-conversation message read receipts were implemented afterward (see `FRONTEND_API_HANDOFF.md`). This follow-up audit found no DERCAS specification requiring dedicated GPS tracking, a global leaderboard, transit-provider operations, or tournament standings. MAP, GAM, TRN and SPT remain on their existing generic module-data routes plus their documented search/progress/route-detail/result operations.

The ADMIN-only collection routes `GET /api/admin/users`, `/events`, `/audit`, `/role-requests`, and `/reports` now support optional bounded database pagination (`page`, `size`); calls without query parameters keep the previous JSON array contract. Pagination metadata is returned in `X-Page`, `X-Page-Size`, `X-Total-Count`, `X-Total-Pages`, and `X-Has-Next` headers while the JSON remains an array. Page size is limited to 100 and results sort by creation time descending. Remote Supabase was not accessed.