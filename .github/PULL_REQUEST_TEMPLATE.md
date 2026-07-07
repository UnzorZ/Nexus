## Summary

<!-- What does this change do, and why? Reference the issue/milestone if any (Closes #N). -->

## Changes

<!-- Bullet list of the meaningful changes (files/groups), not a line-by-line diff. -->

## How to test

<!-- Steps to verify end-to-end (commands, routes, smoke scripts). Note if Testcontainers are involved. -->

## Security / risk notes

<!-- Call out anything security-relevant (new endpoints, authz changes, secrets, cookies, CSRF). Write "None" if N/A. -->

## Checklist

- [ ] Backend: `./gradlew :apps:nexus-api:test` passes
- [ ] Frontend: `cd apps/web && npm run lint && npm run build` passes
- [ ] No new Spring Modulith boundary violation (no cross-module import outside `shared`; no new `@NamedInterface` unless justified)
- [ ] Authorization stays fail-closed by default
