## Version 3.0.0

### Added
- Self-signed JWT tokens now carry per-player `uuid` and `name` claims when issued for a specific player, letting the backend identify the acting player from the token alone without a separate lookup.
- JWT tokens now support an optional audience claim (`aud`), allowing the backend to distinguish mod-issued tokens from other token types.
- An optional UUID-to-name resolver can be passed to `AuthStrategy.selfSignedJwt(...)` to embed the player's display name in the token.

### Breaking
- Removed the login-endpoint authentication path (`AuthStrategy.loginEndpoint(String)`, `LoginEndpointAuthConfig`, and `LoginEndpointStrategy`). Mods that used the login endpoint must migrate to `AuthStrategy.selfSignedJwt(secret, ttl, "createrington.mod", nameResolver)` and configure a shared JWT secret in their mod config.
- Removed the `AuthConfig` sealed interface. `CRNetClient.Builder.auth(...)` now accepts `AuthStrategy` directly instead of the former `AuthConfig` supertype.
