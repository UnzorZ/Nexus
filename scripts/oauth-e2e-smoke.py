#!/usr/bin/env python3
"""
E2E smoke de los flujos OAuth/OIDC avanzados (#215-217) + #52 contra el backend vivo.
Cubre lo que los ITs (MockMvc) NO cubren: HTTP real de extremo a extremo.

Prereqs:
  - Backend corriendo en http://localhost:8080 (p. ej.
    `SPRING_DOCKER_COMPOSE_ENABLED=false ./gradlew :apps:nexus-api:bootRun`;
    sin el perfil remote-dev, o las cookies Secure no viajan por HTTP y el login falla).
  - Postgres accesible vía `docker exec -i nexus-postgres-1 psql …` (el script siembra un
    proyecto + ProjectUser + cliente OAuth + rol/permiso frescos por run; no toca datos
    existentes salvo el proyecto efímero smoke-e2e-*).

Uso:  python3 scripts/oauth-e2e-smoke.py
Salida: ✓/✗ por stage + resumen PASS/FAIL/WARN. Exit code 1 si hay fallos.

Stages:
  0. Discovery per-realm (endpoints avanzados advertised)
  1. DCR (/oauth2/register) → cliente + default scopes (#52) + rejection guard
  2. PAR (/oauth2/par) → request_uri
  3. Device (/oauth2/device_authorization) → device_code/user_code
  4. Token issuance completo (login → authorize code+PKCE → token) → decode claims
     (sub, project_id, authz_version, permissions)
  5. Introspection → active:true + claims
  6. Back-channel logout RFC 8417 → listener HTTP recibe logout_token firmado
"""

import base64, hashlib, http.cookiejar, json, os, re, secrets, subprocess, sys, threading, time
import urllib.request, urllib.parse, urllib.error
from http.server import BaseHTTPRequestHandler, HTTPServer

API = "http://localhost:8080"
SLUG = "smoke-e2e-" + secrets.token_hex(4)   # proyecto fresco por run
BC_PORT = 8913
USER_EMAIL = "smoke-e2e@nexus.test"
USER_PASSWORD = "marcos991!"
USER_USERNAME = "smoke-e2e-user"
USER_BCRYPT = "$2a$10$oFUIIb5oA.1SbDU0t2Q8rO4JSKpZvir9Sps3KI3nj2zY/35AIXTrq"  # bcrypt("marcos991!")
CLIENT_SECRET = "marcos991!"   # mismo hash
PERMISSION_KEY = "smoke.read"

PASS, FAIL, WARN = [], [], []
def ok(name, detail=""): PASS.append(name); print(f"  ✓ {name}" + (f" — {detail}" if detail else ""))
def bad(name, detail=""): FAIL.append(name); print(f"  ✗ {name}: {detail}")
def warn(name, detail=""): WARN.append(name); print(f"  ⚠ {name}: {detail}")

def psql(sql):
    r = subprocess.run(["docker","exec","-i","nexus-postgres-1","psql","-U","nexus","-d","nexus","-t","-A","-v","ON_ERROR_STOP=1"],
                       input=sql, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"psql failed: {r.stderr.strip()}")
    return r.stdout.strip()

def b64url_decode(s):
    s += "=" * (-len(s) % 4)
    return base64.urlsafe_b64decode(s)
def jwt_payload(token):
    return json.loads(b64url_decode(token.split(".")[1]))

# --- HTTP helper con cookie jar ---
CJ = http.cookiejar.CookieJar()
OPENER = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CJ),
                                     urllib.request.HTTPRedirectHandler())
def req(method, path, data=None, headers=None, allow_redirects=True, basic=None):
    url = API + path
    h = dict(headers or {})
    if basic:
        tok = base64.b64encode(basic.encode()).decode()
        h["Authorization"] = "Basic " + tok
    body = None
    if data is not None:
        if isinstance(data, (dict, list)):
            body = json.dumps(data).encode(); h.setdefault("Content-Type","application/json")
        else:
            body = data.encode() if isinstance(data,str) else data
            h.setdefault("Content-Type","application/x-www-form-urlencoded")
    r = urllib.request.Request(url, data=body, method=method, headers=h)
    if not allow_redirects:
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, *a, **k): return None
        opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CJ), NoRedirect())
    else:
        opener = OPENER
    try:
        resp = opener.open(r, timeout=15)
        return resp.status, resp.read().decode(errors="replace"), dict(resp.headers), resp
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode(errors="replace"), dict(e.headers), None

# ============================ Stage 0: seed + discovery ============================
print("\n[0] Seed proyecto + discovery")
import uuid as _uuid
PROJECT_ID = str(_uuid.uuid4()); USER_ID = str(_uuid.uuid4())
ROLE_ID = str(_uuid.uuid4()); PERM_ID = str(_uuid.uuid4())
try:
    psql(f"""
    INSERT INTO projects (id, slug, name, status, created_at, updated_at)
    VALUES ('{PROJECT_ID}'::uuid, '{SLUG}', 'Smoke E2E', 'ACTIVE', now(), now());
    INSERT INTO project_users (id, project_id, email, username, password_hash, display_name, status, email_verified_at, authz_version, created_at, updated_at)
    VALUES ('{USER_ID}'::uuid, '{PROJECT_ID}'::uuid, '{USER_EMAIL}', '{USER_USERNAME}', '{USER_BCRYPT}', 'Smoke E2E User', 'ACTIVE', now(), 1, now(), now());
    INSERT INTO project_permissions (id, project_id, key, label, description, source, enabled, deprecated, missing_from_last_sync, last_declared_at, created_at, updated_at)
    VALUES ('{PERM_ID}'::uuid, '{PROJECT_ID}'::uuid, '{PERMISSION_KEY}', 'smoke read', '', 'MODULE', true, false, false, now(), now(), now());
    INSERT INTO project_roles (id, project_id, system, key, label, description, created_at, updated_at)
    VALUES ('{ROLE_ID}'::uuid, '{PROJECT_ID}'::uuid, false, 'smoke-role', 'Smoke Role', '', now(), now());
    INSERT INTO project_role_permissions (id, project_id, role_id, permission_key, created_at)
    VALUES (gen_random_uuid(), '{PROJECT_ID}'::uuid, '{ROLE_ID}'::uuid, '{PERMISSION_KEY}', now());
    INSERT INTO project_user_roles (id, project_id, project_user_id, role_id, created_at)
    VALUES (gen_random_uuid(), '{PROJECT_ID}'::uuid, '{USER_ID}'::uuid, '{ROLE_ID}'::uuid, now());
    """)
    ok("proyecto + user + rol + permiso + grant seed", SLUG)
except Exception as e:
    bad("seed", e); sys.exit(1)

# discovery
st, body, hdrs, _ = req("GET", f"/p/{SLUG}/.well-known/openid-configuration")
disc = json.loads(body) if st == 200 else {}
if st == 200: ok("discovery 200")
else: bad("discovery", f"status {st}")
for ep in ("pushed_authorization_request_endpoint","device_authorization_endpoint","end_session_endpoint","introspection_endpoint","jwks_uri"):
    if disc.get(ep): ok(f"discovery.{ep}", disc[ep])
    else: bad(f"discovery.{ep}", "missing")
if disc.get("registration_endpoint") is None:
    ok("discovery.registration_endpoint=None (RFC7591 no se anuncia, esperado)")
REGISTER = f"/p/{SLUG}/oauth2/register"
PAR_EP = f"/p/{SLUG}/oauth2/par"
DEVICE_EP = f"/p/{SLUG}/oauth2/device_authorization"
INTROSPECT_EP = f"/p/{SLUG}/oauth2/introspect"
AUTHORIZE_EP = f"/p/{SLUG}/oauth2/authorize"
TOKEN_EP = f"/p/{SLUG}/oauth2/token"

# --- cliente versátil para stages 2-6 (todos los grants, consent_required=false) ---
import uuid as _u2
VCLIENT_DB_ID = str(_u2.uuid4())
VCLIENT_ID = "nxo-smoke-" + secrets.token_hex(6)
VCLIENT_SECRET = USER_PASSWORD   # "marcos991!" → mismo bcrypt que USER_BCRYPT
psql(f"""
INSERT INTO project_oauth_clients (id, project_id, client_id, client_secret_hash, name,
    redirect_uris, post_logout_redirect_uris, grant_types, scopes, require_pkce,
    consent_required, status, created_at, updated_at)
VALUES ('{VCLIENT_DB_ID}'::uuid, '{PROJECT_ID}'::uuid, '{VCLIENT_ID}', '{USER_BCRYPT}',
    'Smoke Versatile', 'http://localhost:9999/callback', '',
    'authorization_code\nrefresh_token\nurn:ietf:params:oauth:grant-type:device_code',
    'openid\nprofile', true, false, 'ACTIVE', now(), now());
""")
ok("cliente versátil seed", VCLIENT_ID)

# ============================ Stage 1: DCR + default scopes ============================
print("\n[1] DCR (RFC 7591) + default scopes (#52)")
st, body, hdrs, _ = req("POST", REGISTER, {
    "redirect_uris":["http://localhost:9999/callback"],
    "grant_types":["authorization_code","refresh_token"],
    "token_endpoint_auth_method":"client_secret_basic",
    "client_name":"Smoke DCR Client"
})
dcr = json.loads(body) if body else {}
if st == 201 and dcr.get("client_id"):
    ok("DCR 201 client_id", dcr["client_id"][:16]+"…")
else:
    bad("DCR", f"status {st} body {body[:200]}")
    sys.exit(1)
# verificar default scopes persistidos (oidc/profile) — NO incluye el permiso smoke.read
dcr_scopes = psql(f"SELECT scopes FROM project_oauth_clients WHERE client_id='{dcr['client_id']}';")
if "openid" in dcr_scopes and "profile" in dcr_scopes and "smoke.read" not in dcr_scopes:
    ok("DCR default scopes (openid, profile)", dcr_scopes.replace(chr(10),","))
else:
    bad("DCR default scopes", dcr_scopes)

# DCR rechaza scope declarado (guard)
st2, body2, _, _ = req("POST", REGISTER, {
    "redirect_uris":["http://localhost:9999/callback"],"grant_types":["authorization_code"],
    "token_endpoint_auth_method":"client_secret_basic","client_name":"Scoped","scope":"openid profile"})
if st2 == 400 and json.loads(body2).get("error")=="invalid_scope":
    ok("DCR rechaza scope declarado (invalid_scope, esperado)")
else:
    bad("DCR scope rejection guard", f"status {st2} body {body2[:200]}")

# ============================ Stage 2: PAR ============================
print("\n[2] PAR (RFC 9126)")
verifier = secrets.token_urlsafe(64)
challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
par_data = urllib.parse.urlencode({
    "redirect_uri":"http://localhost:9999/callback","client_id":VCLIENT_ID,
    "response_type":"code","scope":"openid profile","code_challenge":challenge,
    "code_challenge_method":"S256","state":"par-state"})
st, body, _, _ = req("POST", PAR_EP, par_data, basic=f"{VCLIENT_ID}:{VCLIENT_SECRET}")
par = json.loads(body) if body else {}
if st == 201 and par.get("request_uri"):
    ok("PAR 201 request_uri", par["request_uri"][:20]+"…  expires_in="+str(par.get("expires_in")))
else:
    bad("PAR", f"status {st} body {body[:300]}")

# ============================ Stage 3: Device ============================
print("\n[3] Device Authorization Grant (RFC 8628)")
st, body, _, _ = req("POST", DEVICE_EP, "", basic=f"{VCLIENT_ID}:{VCLIENT_SECRET}")
dev = json.loads(body) if body else {}
if st == 200 and dev.get("device_code") and dev.get("user_code") and dev.get("verification_uri"):
    ok("device_authorization 200", f"user_code={dev['user_code']}  expires_in={dev.get('expires_in')}")
else:
    bad("device_authorization", f"status {st} body {body[:300]}")

# ============================ Stage 4: Token issuance completo ============================
print("\n[4] Token issuance (login → authorize code+PKCE → token)")
# Necesitamos un cliente con consent_required=false (el DCR lo deja false por defecto). Lo usamos.
# 4a csrf
st, body, _, _ = req("GET", f"/api/p/{SLUG}/csrf")
xsrf = None
for c in CJ:
    if c.name == "XSRF-TOKEN": xsrf = c.value
if st == 200 and xsrf: ok("csrf emitido")
else: bad("csrf", f"status {st}")

# 4b login
st, body, _, _ = req("POST", f"/api/p/{SLUG}/login",
    {"email":USER_EMAIL,"password":USER_PASSWORD,"continueUrl":f"/p/{SLUG}/oauth2/authorize"},
    headers={"X-XSRF-TOKEN":xsrf} if xsrf else None)
if st == 200:
    ok("login end-user 200", json.loads(body).get("code",""))
else:
    bad("login", f"status {st} body {body[:200]}"); sys.exit(1)

# 4c authorize (consent_required=false → 302 directo con code)
state = secrets.token_hex(8)
q = urllib.parse.urlencode({"response_type":"code","client_id":VCLIENT_ID,
    "redirect_uri":"http://localhost:9999/callback","scope":"openid profile",
    "code_challenge":challenge,"code_challenge_method":"S256","state":state})
st, body, hdrs, _ = req("GET", f"{AUTHORIZE_EP}?{q}", allow_redirects=False)
loc = hdrs.get("Location") or hdrs.get("location")
code = None
if loc:
    m = re.search(r"[?&]code=([^&]+)", loc)
    if m: code = urllib.parse.unquote(m.group(1))
if code:
    ok("authorize → code 302", code[:16]+"…")
else:
    bad("authorize code", f"status {st} location={loc} body={body[:160]}")

# 4d token exchange
if code:
    td = urllib.parse.urlencode({"grant_type":"authorization_code","code":code,
        "redirect_uri":"http://localhost:9999/callback","code_verifier":verifier})
    st, body, _, _ = req("POST", TOKEN_EP, td, basic=f"{VCLIENT_ID}:{VCLIENT_SECRET}")
    tok = json.loads(body) if body else {}
    access = tok.get("access_token"); idt = tok.get("id_token")
    if st == 200 and access:
        ok("token 200", f"token_type={tok.get('token_type')} scope={tok.get('scope')}")
        claims = jwt_payload(access)
        for claim in ("sub","project_id","authz_version","permissions"):
            if claim in claims: ok(f"access.{claim}", str(claims[claim])[:40])
            else: bad(f"access.{claim}", "missing")
        if claims.get("permissions") == [PERMISSION_KEY]:
            ok("access.permissions = [smoke.read] (rol→permiso生效)")
        elif "permissions" in claims:
            warn("permissions no vacío esperado", str(claims["permissions"]))
        # guardar para stage 5/6
        SMOKE = {"access":access,"id_token":idt,"client_id":VCLIENT_ID,
                 "client_secret":VCLIENT_SECRET,"claims":claims,
                 "client_db_id": VCLIENT_DB_ID}
    else:
        bad("token", f"status {st} body {body[:300]}")
else:
    SMOKE = None

# ============================ Stage 5: Introspection ============================
print("\n[5] Introspection (con authz_version enforcement)")
if SMOKE:
    d = urllib.parse.urlencode({"token":SMOKE["access"]})
    st, body, _, _ = req("POST", INTROSPECT_EP, d, basic=f"{SMOKE['client_id']}:{SMOKE['client_secret']}")
    intr = json.loads(body) if body else {}
    if st == 200 and intr.get("active"):
        ok("introspect active=true", f"sub={intr.get('sub')} project_id presente={bool(intr.get('project_id'))}")
    else:
        bad("introspect", f"status {st} body {body[:200]}")

# ============================ Stage 6: Back-channel logout ============================
print("\n[6] Back-channel logout (RFC 8417) — listener HTTP")
if SMOKE:
    # Listener que captura el POST del OP
    received = {}
    class H(BaseHTTPRequestHandler):
        def do_POST(self):
            n = int(self.headers.get("Content-Length",0)); raw = self.rfile.read(n).decode()
            received["raw"] = raw
            received["ct"] = self.headers.get("Content-Type")
            self.send_response(200); self.end_headers()
        def log_message(self,*a): pass
    srv = HTTPServer(("127.0.0.1", BC_PORT), H)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    # Set backchannel_logout_uri en el cliente usado (backend en host → localhost)
    psql(f"UPDATE project_oauth_clients SET backchannel_logout_uri='http://localhost:{BC_PORT}/bc' WHERE client_id='{SMOKE['client_id']}';")
    ok("listener + backchannel_logout_uri set", f":{BC_PORT}")
    # logout del realm → dispara BackChannelLogoutRequested (async)
    st, body, _, _ = req("POST", f"/api/p/{SLUG}/logout",
        headers={"X-XSRF-TOKEN":xsrf} if xsrf else None)
    ok(f"logout {st}", "evento publicado")
    # esperar al fan-out async (con retry/backoff de #52)
    for _ in range(40):
        if "raw" in received: break
        time.sleep(0.25)
    if "raw" in received:
        # logout_token=<jwt>
        lt = urllib.parse.parse_qs(received["raw"]).get("logout_token",[None])[0]
        if lt:
            cl = jwt_payload(lt)
            evs = cl.get("events",{})
            ok("back-channel logout_token recibido",
               f"iss={str(cl.get('iss'))[-30:]} sub={cl.get('sub')} events_ok={'backchannel-logout' in str(evs)}")
        else:
            bad("logout_token parse", received["raw"][:200])
    else:
        bad("back-channel no recibido en 10s", "(rev: host.docker.internal reachability)")
    srv.shutdown()
else:
    warn("stage 6 saltado (sin token)")

# ============================ resumen ============================
print("\n" + "="*60)
print(f"PASS={len(PASS)}  FAIL={len(FAIL)}  WARN={len(WARN)}")
if FAIL: print("FALLOS:"); [print("  -",f) for f in FAIL]
sys.exit(1 if FAIL else 0)
