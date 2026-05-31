# ADR-0006: Trace Id For Every Request

Status: Accepted

## Context

Nexus needs useful terminal logs and debuggable API errors.

When a request fails, the dashboard, API response, logs, and audit events should be connectable through one identifier.

## Decision

Every HTTP request receives a trace ID.

If the caller sends `X-Trace-Id`, Nexus reuses it when valid. Otherwise Nexus generates one. The trace ID is written to MDC and returned as `X-Trace-Id` in the response.

## Consequences

- Console logs include the current trace ID.
- API errors should include `traceId`.
- Audit events should store `trace_id` when an action happens inside an HTTP request.
