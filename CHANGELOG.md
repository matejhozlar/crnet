## Version 2.8.0

### Added
- Typed `get` and `post` calls now automatically unwrap responses in the `{success, message, data}` envelope format — when the backend wraps its payload in this structure, the typed value is deserialized from the `data` field rather than the whole body. Non-enveloped responses continue to work as before.
- New `Type` overloads for `get` and `post` on both `BackendHttpClient` and `CRNetClient`, enabling parameterized (generic) response types such as `List<T>` or custom wrappers.
- New `getList` convenience methods on `BackendHttpClient` and `CRNetClient` for endpoints that return a JSON array as their typed payload.
- Server-level authentication shorthand overloads for all new `Type` and `getList` methods, matching the existing `Class<T>` API surface.
