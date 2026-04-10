## Version 2.1.2

### Fixed
- Fixed async HTTP requests silently swallowing errors (401, 403, 400, etc.). Failed requests are now logged as errors, and non-2xx responses are logged as warnings, making connection and authentication issues visible in the server log.
