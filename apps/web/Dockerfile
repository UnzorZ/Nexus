# syntax=docker/dockerfile:1
#
# Nexus Web — production image (Next.js, output: 'standalone').
# The BUILD CONTEXT MUST be the repository root (apps/web is copied from it).
#
# NOTE on NEXT_PUBLIC_*: Next.js inlines NEXT_PUBLIC_* at BUILD time, not runtime.
# Pass the browser-facing API URL as a build arg so it is baked into the bundle.
# For a multi-domain deploy, rebuild with the right NEXT_PUBLIC_NEXUS_API_BASE_URL.

# ---- build stage: install deps (cached layer) + next build (traces standalone) ----
FROM node:22-alpine AS build
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
ARG NEXT_PUBLIC_NEXUS_API_BASE_URL=http://localhost:8080
ENV NEXT_PUBLIC_NEXUS_API_BASE_URL=$NEXT_PUBLIC_NEXUS_API_BASE_URL
COPY apps/web/package.json apps/web/package-lock.json ./
RUN npm ci
COPY apps/web ./
RUN npm run build

# ---- runtime stage: standalone server only (no full node_modules) ----
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    HOSTNAME=0.0.0.0 \
    PORT=3000
# .next/standalone contains server.js + a minimal traced node_modules + .next/.
# public/ and .next/static are NOT auto-included by standalone — copy them next
# to server.js so it serves them.
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public
EXPOSE 3000
# Next 16 standalone listens on `localhost` unless HOSTNAME is set (see ENV above).
CMD ["node", "server.js"]
