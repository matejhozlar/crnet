## Version 2.3.1

### Fixed
- Fixed authentication failures that occurred when the JWT secret was 48 bytes or longer. The signing algorithm is now explicitly pinned to HS256, ensuring tokens are always accepted by backends that require HS256.
