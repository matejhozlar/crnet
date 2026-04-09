# CRNet

Shared backend HTTP client library for Createrington NeoForge mods.

Provides JWT authentication, a standardised HTTP client, and a bounded shared
request queue so individual mods do not need to implement their own networking layer.

## Features

- `BackendHttpClient` — `java.net.http.HttpClient` wrapper with HTTP/1.1 enforced, exponential backoff retry, and automatic 401 handling
- `TokenManager` — JWT cache with proactive refresh and thread-safe cross-mod sharing
- `RequestQueue` — shared bounded executor (log-and-drop rejection policy)
- `CRNetConfig` — unified config primitives (base URL, JWT secret, timeouts, retry settings)

## Usage

Declare CRNet as a required dependency in your mod's `build.gradle`:

```groovy
dependencies {
    implementation "com.saunhardy.crnet:crnet:<version>"
}
```

And in `neoforge.mods.toml`:

```toml
[[dependencies.yourmodid]]
    modId = "crnet"
    type = "required"
    versionRange = "[1.0.0,)"
    ordering = "BEFORE"
    side = "SERVER"
```

Then access the shared client from any mod:

```java
CRNet.getRequestQueue().submit(() -> {
    try {
        MyResponse resp = client.post("/your/endpoint", body, MyResponse.class);
    } catch (BackendException e) {
        CRNet.LOGGER.error("Request failed: {}", e.getMessage());
    }
});
```

## Package layout

```
com.saunhardy.crnet
├── CRNet.java                  Main mod class / static accessors
├── config/
│   └── CRNetConfig.java        Config primitives (base URL, timeouts, etc.)
├── auth/
│   ├── TokenManager.java       JWT cache + auto-refresh
│   └── TokenException.java
├── http/
│   ├── BackendHttpClient.java  HTTP/1.1 client with retry + auth
│   └── BackendException.java
└── queue/
    └── RequestQueue.java       Shared bounded executor
```

## Building

```bash
./gradlew build
```

## Publishing to Gitea Packages

```bash
./gradlew publish
```

## Authors

saunhardy
