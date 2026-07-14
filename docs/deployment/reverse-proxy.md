# Reverse proxy / TLS termination

Nexus expects to run behind a TLS-terminating reverse proxy (nginx, Traefik, Caddy,
an ALB, …) in production. The API listens on `8080`; TLS terminates at the edge.

## Why it matters

The OIDC **issuer** and the end-user login/consent/redirect URLs are built from the
incoming request's scheme, host and port (see `ProjectEndUserAuthController#realmIssuer`,
`ConsentController`, `DeviceVerificationController`,
`ProjectOauthAuthenticationEntryPoint`). Without forwarded headers, the app only sees
the proxy→origin hop (`http`, the proxy's host, `8080`), so it would advertise an
`http://...:8080` issuer — which breaks OIDC `iss` matching and produces insecure
(http) session cookies.

`application-prod.properties` sets `server.forward-headers-strategy=native`, so Spring's
`ForwardedHeaderFilter` rewrites `getScheme()`/`getServerName()`/`getServerPort()` from
`X-Forwarded-{Proto,Host,Port}` (or the RFC 7239 `Forwarded` header). It only rewrites
when those headers are present, so direct (no-proxy) deployments are unaffected.

## Trust requirement

`ForwardedHeaderFilter` trusts whatever forwarded headers reach the app — it does **not**
allowlist proxy IPs. Therefore your edge proxy **must strip or overwrite**
`X-Forwarded-*` / `Forwarded` on every inbound request before setting its own values.
Otherwise a client can spoof `X-Forwarded-Proto`/`Host` and make Nexus emit the wrong
issuer/scheme.

Concretely: only expose port `8080` to the reverse proxy. Never publish it directly to
the public internet.

## nginx example

```nginx
server {
    listen 443 ssl http2;
    server_name nexus.example.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host  $host;
        proxy_set_header X-Forwarded-Port  $server_port;
    }
}
```

## Traefik example

```yaml
labels:
  - traefik.http.routers.nexus.rule=Host(`nexus.example.com`)
  - traefik.http.routers.nexus.tls=true
  - traefik.http.services.nexus.loadbalancer.server.port=8080
```

Traefik sets `X-Forwarded-*` by default and strips the corresponding client-supplied
headers when `forwardingHeaders.trustedIPs` is configured — keep that configured to the
proxy chain so untrusted clients cannot inject them.

## Rate limiting

`nexus.ratelimit.trust-forwarded-for` is `false` by default and should stay that way:
`server.forward-headers-strategy=native` already rewrites `getRemoteAddr()` for the
per-IP rate-limit key, which is the safer path. Only set `trust-forwarded-for=true` if
you have a specific topology reason and fully trust the header at that point.
