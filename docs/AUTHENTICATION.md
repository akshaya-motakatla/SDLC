# Account Authentication and Project Ownership

## Account endpoints
- `GET /api/auth/csrf` returns a CSRF token and sets the CSRF cookie.
- `POST /api/auth/register` creates an account and starts an authenticated session.
- `POST /api/auth/login` authenticates by email and password.
- `GET /api/auth/me` returns the current account profile.
- `POST /api/auth/logout` invalidates the current session.

Registration requires a unique username, email address, first and last names, and a password between 12 and 72 characters. Usernames and email addresses are normalized to lowercase. Passwords are stored as BCrypt hashes.

## Session and CSRF behavior
- Authentication state is stored in a server-side Spring Security session; the browser does not store bearer tokens.
- Session cookies are HttpOnly and SameSite=Lax.
- CSRF protection is enabled for registration, login, logout, and all other state-changing API requests. The frontend loads a CSRF token and sends it in `X-XSRF-TOKEN`.
- Unauthenticated API requests return HTTP 401. Invalid credentials return a generic message, and account conflicts return HTTP 409.

## Project ownership
The API derives project ownership from the authenticated account's email. Project list, read, update, and delete queries are scoped to that owner. Requests for another account's project return 404 rather than disclosing whether it exists.
The initial servlet-container session store is in-memory and is not shared across instances. Configure a shared Spring Session store before horizontal scaling.

## Local configuration
Set `DB_USERNAME` and `DB_PASSWORD` in the backend process environment. The sample values in `backend/.env.example` are documentation only; Spring Boot does not load that file automatically. For local HTTP development, `SESSION_COOKIE_SECURE=false`; set `SESSION_COOKIE_SECURE=true` when serving production traffic over HTTPS.

Keep the frontend and API same-origin in production, preferably with a reverse proxy. Cross-origin session deployment is not configured by default and requires an explicit allowlist for CORS plus a reviewed cookie `SameSite` and CSRF configuration.

## Production readiness still required
This is a minimal account/session foundation, not a complete identity-management product. Before a public launch, add password reset and email verification, rate limiting for login and registration, monitoring/alerting for authentication abuse, account deletion and recovery policies, and production HTTPS/reverse-proxy configuration. Do not expose the demo database credentials or use the sample `.env.example` values in production.
