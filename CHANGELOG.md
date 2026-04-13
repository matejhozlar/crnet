## Version 2.7.0

### Added
- Added `persistLastSentTo(Path)` to `HeartbeatBuilder`, allowing heartbeats to record the timestamp of each successful send and reschedule correctly after server restarts. Long-period heartbeats (e.g. daily) no longer re-fire on every boot — the next tick is scheduled relative to the last successful send.
- Added `triggerNow()` to `HeartbeatHandle`, allowing operators to push an immediate out-of-band heartbeat (e.g. via an RCON command) without waiting for the next scheduled tick.
