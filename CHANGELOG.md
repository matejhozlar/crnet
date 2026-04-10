## Version 2.1.3

### Fixed
- Fixed server requests silently failing due to the JJWT library not being bundled correctly inside the mod jar — requests that relied on authentication tokens now work as expected.
- Fixed an issue where certain low-level request errors were silently swallowed and never reported, making failures invisible in logs.
