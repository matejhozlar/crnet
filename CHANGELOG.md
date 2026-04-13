## Version 2.6.0

### Fixed
- Fixed a runtime crash (`NoClassDefFoundError`) when using the `forceloads` package from `createrington-dev-api`. The bundled jar was pinned to 1.0.0, which is missing the `forceloads` classes; it now correctly bundles 1.1.0.

### Changed
- Internal improvements.
