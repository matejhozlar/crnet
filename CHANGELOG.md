## Version 2.2.0

### Added
- Backend responses can now include a player-facing message that is displayed in in-game chat, separate from the internal log message used for diagnostics.

### Changed
- Internal improvements to HTTP response handling and logging.

### Breaking
- The `postAsync` method now returns `CompletableFuture<ApiResponse<Void>>` instead of `CompletableFuture<Void>` — any code calling this method must be updated to unwrap the response object.
- `PlayerPresenceData` has been removed; use `PresenceAPI` directly instead.
