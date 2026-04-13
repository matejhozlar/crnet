## Version 2.5.0

### Added
- Added `HeartbeatBuilder.payloadOn(Executor, Supplier)` for assembling heartbeat payloads on a caller-provided executor. Useful when payload assembly reads thread-confined state such as Minecraft server data or OPAC APIs — `MinecraftServer` implements `Executor` and can be passed directly.

### Fixed
- Fixed potential concurrent heartbeat dispatches when using `payloadOn` with a slow executor — overlapping ticks are now skipped to prevent redundant POST requests and unnecessary load on the payload executor.
- Passing `null` as either argument to `payloadOn` now throws immediately at configuration time rather than silently falling back to the inline path at runtime.
