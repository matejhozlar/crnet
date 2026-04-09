## Version 1.0.0

- Initial release
- HTTP client for communicating with the Createrington backend API
- JWT authentication support (login endpoint and self-signed strategies)
- Player presence heartbeat — periodically syncs the online player list to the backend
- Async request queue for non-blocking fire-and-forget HTTP submissions
- Server-side config: base URL, auth mode, server ID, heartbeat interval and path, connect timeout
