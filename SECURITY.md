# Security

## Agent control (MCP server)

Off by default. When on, the launcher listens on port 8765 of the TV, LAN only, and every request needs the bearer token shown on the TV (Settings > Launcher > Agent control).

- Anyone on your network with the token can change settings, press keys, open apps and take screenshots of the TV. Treat the token like a password; use *New token* to rotate it.
- Do not forward the port to the internet.
- Turn it off when not needed.

## ADB

Debugging over the network gives full control of the TV. Turn it off when done (Developer options > USB debugging / Network debugging) and revoke authorisations.

## Reporting

Found a problem? Open a GitHub issue, or for anything sensitive contact the author via [sanangoyal.com](https://sanangoyal.com).
