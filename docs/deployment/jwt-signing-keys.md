# JWT signing keys (production)

The OAuth2/OIDC Authorization Server signs access tokens and ID tokens with an RSA
key loaded from a PKCS12 keystore (see ADR-0011). A committed **development**
keystore ships with the app so local dev and tests are zero-config. **Production
must provision its own keystore and configure it via environment variables.**
Reusing the committed dev key in production is insecure: it is public, so anyone
with the repo could forge tokens.

## 1. Generate the production keystore

Pick a strong, random password (do **not** reuse the dev `devpassword`). Use the
**same** value for the keystore and the key password (modern JDKs coerce the PKCS12
key password to the store password anyway, and this avoids surprises).

```bash
# Strong random password (keep it in your secrets manager, NOT in the repo)
KEYSTORE_PASSWORD="$(openssl rand -base64 32)"

# Create the keystore: RSA 3072 (2048 minimum accepted), 10-year validity.
keytool -genkeypair \
  -alias nexus-prod-key \
  -keyalg RSA -keysize 3072 \
  -sigalg SHA384withRSA \
  -keystore /etc/nexus/jwt-keystore.p12 \
  -storetype PKCS12 \
  -storepass "$KEYSTORE_PASSWORD" \
  -keypass  "$KEYSTORE_PASSWORD" \
  -validity 3650 \
  -dname "CN=nexus-prod, O=YourOrg, C=XX"

# Lock down permissions. The user running the API must be able to read it.
chown <api-user>:<api-group> /etc/nexus/jwt-keystore.p12
chmod 600 /etc/nexus/jwt-keystore.p12
```

Verify:

```bash
keytool -list -keystore /etc/nexus/jwt-keystore.p12 -storepass "$KEYSTORE_PASSWORD"
```

## 2. Configure the API (environment variables)

All four must be set in production. `keystore-location` accepts `file:`, `classpath:`
prefixes or a bare absolute path (`/etc/...`).

```bash
export NEXUS_OAUTH_JWK_KEYSTORE_LOCATION="file:/etc/nexus/jwt-keystore.p12"
export NEXUS_OAUTH_JWK_KEYSTORE_PASSWORD="$KEYSTORE_PASSWORD"
export NEXUS_OAUTH_JWK_KEY_ALIAS="nexus-prod-key"
export NEXUS_OAUTH_JWK_KEY_PASSWORD="$KEYSTORE_PASSWORD"
```

(Or whatever your orchestrator uses: Kubernetes secret env vars, Docker secrets,
etc. Never commit the production password.)

## 3. Verify readiness

The `jwkSigningKey` indicator is part of the readiness health group. With a keystore
configured it is `UP`:

```bash
curl -s http://localhost:8080/actuator/health/readiness
```

If it reports `DOWN` with `keystoreConfigured: false`, the app fell back to ephemeral
keys — fix the four variables and restart. The public key is published at
`/oauth2/jwks`.

## 4. Rotation (single active key)

The app exposes a **single** signing key by design (see ADR-0011). Rotating it
invalidates outstanding access tokens until clients refresh — that is accepted by
decision. To rotate:

1. Generate a new keystore (step 1) with a fresh key — or use the bundled helper:
   `KEYSTORE=/etc/nexus/jwt-keystore.p12 ./scripts/rotate-jwk.sh` (it generates
   the keystore, sets `chmod 600`, and prints the four `NEXUS_OAUTH_JWK_*` values
   to wire into the environment).
2. Replace the file at `NEXUS_OAUTH_JWK_KEYSTORE_LOCATION` (or point the env to the
   new file) and redeploy.
3. Outstanding access tokens signed with the previous key stop validating until
   clients use their **refresh token** (persisted in PostgreSQL) to mint a new one.

Because refresh tokens survive, disruption is limited to access tokens already in
flight. Schedule rotations during low-traffic windows.

## Security checklist

- [ ] Production keystore password is strong, random, and stored in a secrets manager — never in version control.
- [ ] Keystore file is readable only by the API user (`chmod 600`).
- [ ] All four `NEXUS_OAUTH_JWK_*` variables are set in every production environment.
- [ ] `/actuator/health/readiness` is `UP` (keystore loaded, not ephemeral).
- [ ] The committed `dev-jwk.p12` is **not** used in production.
