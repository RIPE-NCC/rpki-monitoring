## HEAD

## v0.6.0
  * Introduce `connect-to` configuration for testing alternative RRDP locations
    (i.e. inactive CDNs) including TLS handshakes.
  * Monitor repository on Cloudflare CDN
  * Track disposed objects in repositories so that resigned objects from core
    are not reported as false differences.
  * Add endpoints to view repository information:
    - `/diff?lhs=<repo1>&rhs=<repo2>&threshold=0`
    - `/<repo>/info?theshold=0`
    - `/<repo>/inspect?uri=<uri>`
    - `/<repo>/objects?uri=<uri>&threshold=0`
  * Change rsync target configuration to make it similar to rrdp.
  * Enable RenovateBot and apply updates
  * Build and run on java 17

## v0.5.5
  * Monitor i3d test endpoint

## v0.5.4
  * remove rpki2 from configuration

## v0.5.3
  * clarify that memory may be set from environment (...)
  * Monitor i3d endpoint

## v0.5.2
  * increase Xmx to 3.5GB for the container.

## v0.5.1
  * spring boot: 2.6.2 -> 2.6.3

## v0.4.5

  * spring-boot: 2.5.1 -> 2.5.4
  * rpki-commons: 1.21 -> 1.24
  * Update Gradle build plugins

## v0.4.4
  * Log unknown objects when encountered.
  * Monitor RRDP repository at `https://publish.rpki.prepdev.ripe.net`

## v0.3.2
  * Set `JAVA_TOOL_OPTIONS` in docker container.

## v0.3.1
  * Hotfix

## v0.3.0

  * Add object expiry monitoring
