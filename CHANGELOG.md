## Version 2.0.0

**Breaking change** — CRNet is now a generic, multi-instance API client library.

- Redesigned as `CRNetClient` with a fluent `Builder` — each consuming mod creates its own client with a base URL and auth strategy
- Added `AuthStrategy` static factories: `selfSignedJwt(secret)`, `loginEndpoint(path)`, `none()`
- Added `HeartbeatBuilder` / `HeartbeatHandle` for consumer-configured periodic heartbeats
- All HTTP methods return `CompletableFuture` — callers can chain async or `.join()` for synchronous results
- `CRNetConfig` stripped to global infrastructure only: queue capacity, timeouts, max retries
- Removed all per-client config from `CRNetConfig`: base URL, auth mode, JWT secret, login endpoint, heartbeat settings, server ID
- Removed static accessors from `CRNet` (`getTokenManager()`, `getHttpClient()`, `getHeartbeatService()`)
- Removed `HeartbeatService` — replaced by generic `HeartbeatBuilder`/`HeartbeatHandle`
- `BackendHttpClient` now conditionally skips the `Authorization` header when using `AuthStrategy.none()`
- Auth strategies accept constructor parameters instead of reading from global config
