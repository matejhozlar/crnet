## Version 2.8.4

### Fixed
- Fixed a crash ("CRNet has not been initialised") that occurred when joining a second server session in the same game session (e.g. leaving a multiplayer server and opening a singleplayer world). The shared HTTP client and request queue are now kept alive for the entire mod lifetime instead of being torn down on server stop.
