# NEBA logviewer (Java 11+)

Log tailing WebSocket implementation for **AEM 6.5 and 6.6** when running on **Java 11+**.

This bundle provides the Jetty 9 WebSocket implementation, compiled for Java 11+. It is used for both AEM 6.5 (Java 11) and AEM 6.6 (Java 17/21). The NEBA delivery package embeds the full Jetty 9.4 stack because AEM does not export these packages for use by other bundles.

## Version mapping

| Java | AEM | Jetty | Bundle |
|------|-----|-------|--------|
| 8 | 6.5 | 9.x | logviewer-java8 |
| 11 | 6.5 | 9.x | logviewer-java11 |
| 17 / 21 | 6.6 | 9.x (embedded) | logviewer-java11 |

AEM 6.6 uses Jetty 11 internally, but the NEBA delivery embeds Jetty 9.4 for the logviewer so one codebase supports both AEM versions.

## Reusable components

The `logviewer-common` module provides shared components:

- **LogOutput** – abstraction for sending bytes/text to the client
- **Tail** – Mode enum (TAIL, FOLLOW)
- **TailRunner** – file tailing logic (uses LogOutput)
- **TailCommand** – parses `tail:0.1mb:/path` and `follow:0.1mb:/path` commands

The `logviewer-java8` and `logviewer-java11` bundles both implement `RemoteEndpointLogOutput` for Jetty 9's API and wire it to `TailRunner` and `TailCommand`.
