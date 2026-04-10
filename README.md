# CRNet

Shared backend HTTP client library for Createrington NeoForge mods.

Provides JWT authentication, a standardised HTTP client, and a bounded shared
request queue so individual mods do not need to implement their own networking layer.

📖 **[Read the full wiki](https://gitea.matejhoz.com/Createrington/crnet/wiki)** for in-depth documentation, guides, and API reference.

## Features

- **`CRNetClient`** — multi-instance API client with a fluent builder; each consuming mod creates its own instance
- **`BackendHttpClient`** — `java.net.http.HttpClient` wrapper with HTTP/1.1 enforced, exponential backoff retry, and automatic 401 handling
- **`TokenManager`** — JWT cache with proactive refresh and thread-safe cross-mod sharing
- **Pluggable auth strategies** — self-signed HS256 JWT, login-endpoint-based auth, or no auth
- **`HeartbeatBuilder`** — fluent API for scheduling periodic heartbeat POSTs on a daemon thread
- **`RequestQueue`** — shared bounded executor (log-and-drop rejection policy)
- **`CRNetConfig`** — unified config primitives (base URL, JWT secret, timeouts, retry settings)

## Installation

### 1. Add the Maven repository

CRNet is published to a git-based Maven repository. Add it to the `repositories` block in your `build.gradle`:

```groovy
repositories {
    maven { url = "https://raw.githubusercontent.com/matejhozlar/maven/main" }
}
```

### 2. Add the dependency

In the `dependencies` block of your `build.gradle`:

```groovy
dependencies {
    implementation "com.saunhardy:crnet:2.3.0"
}
```

### 3. Declare the mod dependency

In your `neoforge.mods.toml` (or the template file under `src/main/templates/META-INF/`), add CRNet as a required dependency so NeoForge loads it before your mod:

```toml
[[dependencies.yourmodid]]
    modId = "crnet"
    type = "required"
    versionRange = "[2.0.0,)"
    ordering = "BEFORE"
    side = "SERVER"
```

Replace `yourmodid` with your mod's ID.

## Setup

### Creating a client

Build a `CRNetClient` with your backend's base URL and an auth strategy:

```java
CRNetClient client = new CRNetClient.Builder()
        .baseUrl("http://localhost:5001")
        .auth(AuthStrategy.selfSignedJwt("your-jwt-secret"))
        .build();
```

### Authentication strategies

CRNet ships with three auth strategies:

| Strategy | Factory method | Description |
|---|---|---|
| Self-signed JWT | `AuthStrategy.selfSignedJwt(secret)` | HS256 JWT with 60 s TTL (server-level auth) |
| Self-signed JWT (custom TTL) | `AuthStrategy.selfSignedJwt(secret, ttlSeconds)` | HS256 JWT with a custom TTL |
| Login endpoint | `AuthStrategy.loginEndpoint("/auth/login")` | Per-player auth via a backend login endpoint |
| None | `AuthStrategy.none()` | No `Authorization` header is sent |

### Sending requests

**Fire-and-forget POST:**

```java
client.postAsync("/api/presence", json);
```

**Async POST with typed response:**

```java
CompletableFuture<ApiResponse<MyData>> future =
        client.post("/api/data", json, MyData.class);
```

**Async GET with typed response:**

```java
CompletableFuture<ApiResponse<Balance>> future =
        client.get("/api/balance", Balance.class);
```

All requests are submitted through the shared `RequestQueue`, so they are bounded and non-blocking.

### Heartbeat scheduling

Use the heartbeat builder to send periodic POSTs (e.g. presence heartbeats):

```java
HeartbeatHandle heartbeat = client.heartbeat()
        .endpoint("/api/presence/heartbeat")
        .interval(5, TimeUnit.MINUTES)
        .payload(() -> buildHeartbeatPayload(server))
        .start();

// On server shutdown
heartbeat.stop();
```

### Token cleanup

Invalidate cached tokens when a player disconnects:

```java
client.invalidateToken(playerUuid);
```

## Package layout

```
com.saunhardy.crnet
├── CRNet.java                  Main mod class / static accessors
├── CRNetClient.java            Multi-instance API client (Builder pattern)
├── HeartbeatBuilder.java       Fluent heartbeat configuration
├── HeartbeatHandle.java        Heartbeat lifecycle handle
├── config/
│   └── CRNetConfig.java        Config primitives (base URL, timeouts, etc.)
├── auth/
│   ├── AuthStrategy.java       Pluggable auth interface + static factories
│   ├── AuthConfig.java         Sealed auth config base
│   ├── SelfSignedJwtStrategy.java  HS256 JWT implementation
│   ├── LoginEndpointStrategy.java  Login-endpoint-based auth
│   ├── LoginEndpointAuthConfig.java  Deferred config for login endpoint auth
│   ├── NoneAuthStrategy.java   No-op auth
│   ├── TokenManager.java       JWT cache + auto-refresh
│   └── TokenException.java
├── http/
│   ├── BackendHttpClient.java  HTTP/1.1 client with retry + auth
│   ├── BackendException.java
│   └── ApiResponse.java        Generic typed response wrapper
├── queue/
│   └── RequestQueue.java       Shared bounded executor
└── util/
    └── UrlUtils.java           URL utilities
```

## Building

```bash
./gradlew build
```

## Publishing to Gitea Packages

```bash
./gradlew publish
```

## Requirements

- Java 21
- Minecraft 1.21.1
- NeoForge 21.1.217+

## Documentation

For detailed documentation, visit the [CRNet Wiki](https://gitea.matejhoz.com/Createrington/crnet/wiki):

- [Getting Started](https://gitea.matejhoz.com/Createrington/crnet/wiki/Getting-Started) — Installation and first steps
- [Creating a Client](https://gitea.matejhoz.com/Createrington/crnet/wiki/Creating-a-Client) — Builder API and lifecycle
- [Authentication](https://gitea.matejhoz.com/Createrington/crnet/wiki/Authentication) — Auth strategies and token management
- [Making Requests](https://gitea.matejhoz.com/Createrington/crnet/wiki/Making-Requests) — POST, GET, and async patterns
- [Heartbeats](https://gitea.matejhoz.com/Createrington/crnet/wiki/Heartbeats) — Periodic request scheduling
- [Configuration](https://gitea.matejhoz.com/Createrington/crnet/wiki/Configuration) — Common config and tuning
- [Error Handling](https://gitea.matejhoz.com/Createrington/crnet/wiki/Error-Handling) — Exceptions, retries, and failure modes
- [Architecture](https://gitea.matejhoz.com/Createrington/crnet/wiki/Architecture) — Internals and design decisions
- [API Reference](https://gitea.matejhoz.com/Createrington/crnet/wiki/API-Reference) — Full class and method reference

## Authors

saunhardy
