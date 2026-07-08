#!/usr/bin/env bash
#
# Nexus — rotate the JWT signing keystore.
#
# Generates a fresh PKCS12 keystore (RSA 3072, SHA384withRSA, 10y) for signing
# OAuth access/ID tokens, prints the four NEXUS_OAUTH_JWK_* values to wire into
# the environment, and reminds you of the rotation consequences (ADR-0011:
# rotation is NOT graceful — there is no key overlap).
#
# See docs/deployment/jwt-signing-keys.md for the full procedure and rationale.
#
# Examples:
#   ./scripts/rotate-jwk.sh
#   KEYSTORE=/etc/nexus/jwt-keystore.p12 ALIAS=nexus-prod-key ./scripts/rotate-jwk.sh
#   DNAME="CN=nexus, O=Acme" KEYSTORE=/tmp/nx.p12 ./scripts/rotate-jwk.sh
#
set -euo pipefail

KEYSTORE="${KEYSTORE:-./jwt-keystore.p12}"
ALIAS="${ALIAS:-nexus-prod-key}"
DNAME="${DNAME:-CN=nexus, O=Nexus}"
VALIDITY="${VALIDITY:-3650}"   # days (10y)
KEYSIZE="${KEYSIZE:-3072}"

if ! command -v keytool >/dev/null 2>&1; then
    echo "error: 'keytool' not found on PATH (ship a JDK)." >&2
    exit 1
fi

# Prefer an explicit password arg; otherwise generate a strong random one.
if [ -z "${PASSWORD:-}" ]; then
    if [ -n "${KEYSTORE_PASSWORD:-}" ]; then
        PASSWORD="${KEYSTORE_PASSWORD}"
    else
        PASSWORD="$(openssl rand -base64 32 2>/dev/null || dd if=/dev/urandom bs=24 count=1 2>/dev/null | base64)"
        echo "(no PASSWORD set — generated a random one; store it securely)" >&2
    fi
fi

echo "Generating keystore: ${KEYSTORE} (alias=${ALIAS}, RSA ${KEYSIZE}, validity=${VALIDITY}d)"
keytool -genkeypair \
    -alias "${ALIAS}" \
    -keyalg RSA -keysize "${KEYSIZE}" -sigalg SHA384withRSA \
    -keystore "${KEYSTORE}" \
    -storepass "${PASSWORD}" -keypass "${PASSWORD}" \
    -dname "${DNAME}" -validity "${VALIDITY}"

chmod 600 "${KEYSTORE}"
echo "wrote ${KEYSTORE} (mode 600)"
echo
echo "Verify (optional):  keytool -list -keystore ${KEYSTORE} -storepass ***"
echo
echo "=== Wire these into the environment (compose.prod.yaml / your secrets) ==="
echo "NEXUS_OAUTH_JWK_KEYSTORE_LOCATION=file:${KEYSTORE}"
echo "NEXUS_OAUTH_JWK_KEYSTORE_PASSWORD=${PASSWORD}"
echo "NEXUS_OAUTH_JWK_KEY_ALIAS=${ALIAS}"
echo "NEXUS_OAUTH_JWK_KEY_PASSWORD=${PASSWORD}"
echo
echo "=== Rotation consequences (ADR-0011) ==="
echo "- There is NO key overlap. Outstanding access tokens signed with the previous"
echo "  key stop validating until their holder uses a persisted refresh token to mint"
echo "  a new one. Plan rotation during a low-traffic window."
echo "- Restart the API so the new keystore is loaded; confirm readiness reports"
echo "  jwkSigningKey UP with keystoreConfigured=true."
echo "- Keep ${PASSWORD} in a secrets manager — without it the keystore is unusable."
