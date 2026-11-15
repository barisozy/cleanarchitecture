# Security Policy

## Supported versions

This repository is a **template**. The version that is actually deployed is supported through a
single line: the default branch of the repository.

| Version | Supported |
|---|---|
| `main` | yes |

## Reporting a vulnerability

Please do **not** open security vulnerabilities as a public GitHub issue.

- The source code of the template is public by design and is the artefact under review, not a
  finding. The reportable subject is a vulnerability in an application **running from a copy** of
  this template.
- Open a **private security advisory** through the repository's *Security* tab. Do not disclose the
  finding in a public issue or pull request.
- If a private advisory is not available, contact the maintainers through the address published in
  the repository profile.

Please include as much of the following as possible:

- The affected version / commit
- Steps to reproduce
- The impact (which data or capability is affected)
- A suggested remediation, if you have one

## Response times

| Stage | Target |
|---|---|
| Initial triage | 7 days |
| Remediation plan | 14 days |
| Remediation (critical) | 30 days |

## Security model

- **Access tokens** — HS256 JWT signed with a shared secret, with the claims `iss`, `aud`, `sub`,
  `iat`, `nbf`, `exp` and `roles` (exactly one role, no `jti`, no e-mail address). The decoder
  validates the issuer, the audience, the timestamps, the subject (a UUID) and the role claim. The
  lifetime is 60 minutes.
- **Transport** — the access token travels in the `Authorization` header. No endpoint writes a
  cookie, so the API has no session, no refresh token, no logout endpoint and no CSRF surface.
- **Passwords** — BCrypt with a configurable cost factor (`BCRYPT_STRENGTH`, default 10). Existing
  hashes keep their own cost. A hash is a domain value object, so it travels through the application
  layer as an opaque value; it is never logged and never returned by the API.
- **Roles** — `USER` and `ADMIN`, stored in one `users.role` column. The web layer checks that the
  caller is authenticated; the application layer enforces ownership. A resource the caller may not
  see is reported as `404`, never as `403`, so its existence is not disclosed.
- **Token lifecycle** — the role is embedded when the token is issued. Changing `users.role` does not
  change an already-issued token; the new role takes effect on the next login, and the old token is
  valid until it expires (at most 60 minutes). There is no revocation list and no logout endpoint.
  Rotating `JWT_SECRET` invalidates every issued token at once; it is the only global revocation
  mechanism, and it requires a restart in every instance.
- **Errors** — RFC 9457 `ProblemDetail` with a stable `code` and a `correlationId`. No stack traces
  and no internal messages in a response.
- **Protected `main`** — a repository ruleset requires a pull request with the CI checks, allows only
  squash merges and blocks force-pushes and branch deletion. The repository administrator can bypass
  it in an emergency.
- **Transport hardening** — explicit CORS allow-list without credentials, HSTS,
  `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer` and
  `Cache-Control: no-store` on the token responses.
- **Deployment surface** — the `prod` profile disables `/v3/api-docs` and `/scalar` and requires
  the issuer, audience, public base URL and problem base URI to be stated explicitly. The health
  probes stay public; every other actuator endpoint requires authentication.

See [`docs/architecture.md`](docs/architecture.md) for the full design.

## Accepted risks

The template is deliberately small, and the following gaps are known and accepted. They are listed
here instead of being hidden behind a feature that only looks like a defence.

| Id | Risk | Mitigation / next step |
|---|---|---|
| **R1** | **No application-level rate limiting.** The credential endpoints are not throttled, so a brute-force or credential-stuffing attack is not slowed down by the application. | Throttle `register` and `login` at the edge or in the WAF. Add account lockout, MFA and a CAPTCHA when the risk demands it. |
| **R2** | **No refresh token.** A 60-minute access token cannot be renewed, so the client has to log in again when it expires. | Acceptable for a first deployment. When the UX suffers, add a refresh token with an ADR, a rotation scheme and a revocation store. |
| **R3** | **An HS256 secret leak lets an attacker forge every token.** The secret is symmetric, so both the issuer and the verifier hold it. | At least 32 random bytes from the environment, never committed, rotated regularly. Move to RS256 with a JWKS endpoint as soon as a second service verifies the tokens. |
| **R4** | **BCrypt truncates at 72 bytes.** `matches()` silently ignores everything after the 72nd byte, so two passwords with a common 72-byte prefix would be interchangeable. | The `RawPassword` guard rejects a password longer than 72 bytes **before** the encoder is called — `forLogin` only checks the byte limit, `forRegistration` also enforces the 12-character minimum — and `AuthService.login` verifies the guard before any hash lookup. The truncation is therefore unreachable. |
| **R5** | **Registration reveals whether an e-mail address is registered.** A second registration returns `409 auth.email-already-registered`, so the endpoint can be used to enumerate accounts. | Acceptable while the API is not a public sign-up surface. To remove the oracle, accept the registration and send a verification e-mail, or return the same response for a new and an existing address. |

## Development-only credentials

The `local` profile seeds two fixed accounts on first start:

| Account | Password | Role |
|---|---|---|
| `demo@example.com` | `DemoPassword123!` | `USER` |
| `admin@example.com` | `AdminPassword123!` | `ADMIN` |

They exist in the `local` profile only, and the `local` profile must never be active in a deployed
environment. Every other environment supplies `JWT_SECRET` from the environment and refuses to
start without it.
