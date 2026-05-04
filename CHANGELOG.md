## Version 3.0.4

### Fixed
- Fixed a memory leak where heartbeats started by dependent mods could outlive the server if the mod forgot to stop them on shutdown. Heartbeats are now automatically stopped when the server stops.
- Fixed excessive memory usage caused by HTTP responses retaining their raw body string after successful deserialization. Only error responses now keep the raw body for diagnostics.
