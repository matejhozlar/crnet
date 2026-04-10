## Version 2.3.0

### Added
- Added a configurable thread pool size (`threadPoolSize`, default 3, range 1–10) in `crnet-common.toml`. The request queue now processes requests concurrently across multiple threads, preventing a single slow backend call from blocking others.
- Added opt-in request logging (`logRequests` setting in `crnet-common.toml`). When enabled, logs every outgoing request URL and response status code at INFO level. Disabled by default.
