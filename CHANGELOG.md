## Version 2.1.0

### Added
- Added `CRNetClient.close()` for explicit lifecycle management — consuming mods can now cleanly release a client on server stop or config reload without relying on garbage collection.

### Fixed
- Fixed the shared HTTP client not being closed on server stop, which caused a thread pool leak.
- Fixed `UrlUtils.safeJoin` appending a trailing slash when called with an empty path, which could result in 404s or silent redirects.
