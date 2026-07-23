# Google federation for project users

A Nexus project can use Google as an external OpenID Connect (OIDC) identity provider.
With this configuration, a project user can do the login with Google instead of a password.
The Google identity is a login method for a `ProjectUser`. It does not change how Nexus
issues OAuth/OIDC tokens for the clients of the project.

This document tells you how to configure Google as an identity provider and how the login
works.

## What is in scope

Google federation applies only to `ProjectUser` accounts (the end users of a project
realm). It does not apply to `NexusAccount` accounts (the operator panel). Read
[accounts-and-project-users.md](accounts-and-project-users.md) for the difference between
these two account types.

## Overview of the flow

Nexus uses the standard OAuth 2.0 authorization-code flow. Google is the identity provider.
Nexus is the relying party.

```text
Browser          Nexus API                  Google
   |                |                          |
   |  click "Sign in with Google"             |
   |-------------->|                          |
   |                |  state + nonce (session)|
   |                |  redirect to Google     |
   |<--------------|                          |
   |  user consents |                         |
   |----------------------------------------->|
   |  redirect back with code + state         |
   |<-----------------------------------------|
   |                |  exchange code          |
   |                |  verify id_token        |
   |                |  resolve the account    |
   |<--------------|                          |
   |  session set, resume the OAuth flow      |
```

The main class is
[`GoogleFederationService.java`](../../apps/api/src/main/java/dev/unzor/nexus/identity/application/service/GoogleFederationService.java).

## Configure Google for a project

Each project keeps its own Google configuration. Nothing is shared between projects, and
nothing is hard-coded. A project administrator stores these values:

| Field | Purpose |
|-------|---------|
| `clientId` | The Google OAuth client ID of the project |
| `clientSecret` | The Google OAuth client secret (stored encrypted) |
| `issuer` | The issuer of the id_token (default: `https://accounts.google.com`) |
| `scope` | The scopes sent to Google (default: `openid email profile`) |
| `enabled` | Set to `true` to turn the Google login on |
| `autoProvision` | Set to `true` to create a new account when no account matches |

The client secret is encrypted at rest with AES-256-GCM. The plain-text secret is never
stored and never returned by the API. The class
[`OidcFederationCrypto.java`](../../apps/api/src/main/java/dev/unzor/nexus/shared/security/OidcFederationCrypto.java)
does the encryption with the master key `nexus.vault.master-key`.

### Store the configuration

Use the panel API. You need the `MANAGE` permission on the project.

```bash
curl -X PUT "https://nexus.example.com/api/panel/v1/projects/$PROJECT_ID/federation/google" \
  -H "Content-Type: application/json" \
  -d '{
        "clientId": "1234567890-abc.apps.googleusercontent.com",
        "clientSecret": "GOCSPX-the-client-secret",
        "enabled": true,
        "autoProvision": false
      }'
```

To read the configuration:

```bash
curl "https://nexus.example.com/api/panel/v1/projects/$PROJECT_ID/federation/google"
```

To remove the configuration:

```bash
curl -X DELETE "https://nexus.example.com/api/panel/v1/projects/$PROJECT_ID/federation/google"
```

When you update the configuration, you can leave `clientSecret` blank. If you do this, Nexus
keeps the existing secret.

### Configure the Google project

Do these steps in the Google Cloud Console:

1. Create an OAuth client of type **Web application**.
2. Add the authorized redirect URI:
   `https://nexus.example.com/api/p/{projectSlug}/login/google/callback`.
   Replace `{projectSlug}` with the slug of your project.
3. Copy the client ID and the client secret into the Nexus configuration.
4. Configure the OAuth consent screen.

The redirect URI is built from the request origin and the project slug. The class
[`GoogleOidcLoginController.java`](../../apps/api/src/main/java/dev/unzor/nexus/identity/api/controller/GoogleOidcLoginController.java)
defines the endpoints.

## The login endpoints

| Method and path | Purpose |
|-----------------|---------|
| `GET /api/p/{slug}/login/google` | Starts the login. Redirects the browser to Google. |
| `GET /api/p/{slug}/login/google/callback` | The Google redirect target. Ends the login. |
| `POST /api/p/{slug}/login/google/link` | Completes the account link after re-authentication. |

The `continue` query parameter of the first endpoint is optional. Use it to resume an
OAuth authorization flow after the login. Nexus accepts only a resume path of the same
project realm (`/p/{slug}/oauth2/...`). Any other value is ignored, and the user goes to
the portal home page. This rule prevents open-redirect attacks.

## How the id_token is verified

Nexus gets the id_token from Google and verifies it before it trusts any claim. The class
[`GoogleIdTokenVerifier.java`](../../apps/api/src/main/java/dev/unzor/nexus/identity/application/service/GoogleIdTokenVerifier.java)
does these checks:

- The signature is valid. Nexus uses the public keys from
  `https://www.googleapis.com/oauth2/v3/certs`.
- The issuer (`iss`) is `https://accounts.google.com` or `accounts.google.com`.
- The audience (`aud`) contains the client ID of the project.
- The token is not expired (`exp`).
- The subject (`sub`) is present.
- The email is present.
- The email is verified (`email_verified` is `true`).
- The `nonce` in the token equals the nonce that Nexus sent.

If any check fails, the login stops. The user sees an error on the login page.

## How the account is resolved

After the id_token is verified, Nexus resolves the Google identity to a `ProjectUser`. The
rules are important for security.

### 1. The Google subject is already linked

If the Google subject (`sub`) is linked to a `ProjectUser`, Nexus logs that user in. This
is the normal case for a return visit. No password is necessary, because the link was made
before.

### 2. The email matches an existing account, but the subject is not linked

A verified Google email can match an existing `ProjectUser` of the project. In this case,
Nexus does **not** log the user in, and it does **not** link the accounts.

This is a hard product rule: a verified email match alone is never a reason to merge or to
log in. The user must re-authenticate first. Nexus stores a temporary ticket in the session
and asks the user for the password of the existing account.

The `POST /api/p/{slug}/login/google/link` endpoint completes this step. It verifies the
password. Only when the password is correct does Nexus create the link and set the session.
A wrong password does not create a link.

### 3. No account matches

If no `ProjectUser` has the Google subject and no `ProjectUser` has the email, then the
Google identity is new. What Nexus does depends on the `autoProvision` flag:

- `autoProvision = true`: Nexus creates a new `ProjectUser`. The account is active and the
  email is verified (Google proved it). Nexus links the Google subject to the new account.
- `autoProvision = false`: the login fails with the error `account_not_found`. No account is
  created.

## Edge cases that Nexus handles

| Case | Result |
|------|--------|
| The Google subject is linked to another account | The unique constraint stops the second link. The login fails with `already_linked`. |
| The Google email is not verified | The login fails with `email_not_verified`. |
| Google sends an error on the callback | The login fails with `provider_error`. |
| The state is replayed or missing | The login fails with `invalid_state`. The state is single-use. |
| The nonce does not match | The login fails with `invalid_nonce`. |
| Federation is not configured or is disabled | The login fails with `not_configured` or `disabled`. |

## Federation links

A link is the durable proof that a Google subject and a `ProjectUser` are the same person.
The entity is
[`ProjectUserOidcLink.java`](../../apps/api/src/main/java/dev/unzor/nexus/identity/domain/entity/ProjectUserOidcLink.java).

The database keeps these constraints:

- One `(project_id, provider, subject)` maps to one `ProjectUser`.
- One `ProjectUser` has at most one link per provider.

Because of these constraints, a Google identity always resolves to exactly one account in a
project.

## Configuration properties

These properties are under the prefix `nexus.identity.federation`. They have working
defaults, so you do not have to set them.

| Property | Default | Purpose |
|----------|---------|---------|
| `google.issuer` | `https://accounts.google.com` | The accepted issuer of the id_token |
| `google.alternateIssuer` | `accounts.google.com` | The second accepted issuer |
| `google.authorizationEndpoint` | `https://accounts.google.com/o/oauth2/v2/auth` | The Google authorization endpoint |
| `google.tokenEndpoint` | `https://oauth2.googleapis.com/token` | The Google token endpoint |
| `google.jwksUri` | `https://www.googleapis.com/oauth2/v3/certs` | The Google public-key set |
| `google.scope` | `openid email profile` | The scopes sent to Google |
| `stateTtl` | `5m` | The lifetime of the login state |
| `linkTicketTtl` | `10m` | The lifetime of the re-authentication ticket |

## Security notes

- The client secret is encrypted at rest. Use a strong value for `nexus.vault.master-key`.
  Do not use the development default value in production.
- The state and the nonce are random, single-use values. They stop replay and login
  injection.
- Nexus never merges accounts because of a verified email. A link needs re-authentication,
  or it is part of the creation of a new account.
- A provisioned account has a random password that no person knows. Password login is not
  possible for such an account. The user must use Google.
