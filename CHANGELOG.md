## HEAD

## v0.12.0
  * Add `rpkimonitoring_expiry_matcher` metrics to match expiry time for
    objects in specific locations.

## v0.11.3
  * Use resolver that prefers IPv6. Add metrics for HTTP clients that do not
    use URL in labels.

## v0.11.2
  * Improve exception handling - track aborted non-failure cases

## v0.11.1
  * Track a non-modified snapshot as successful update
  * Do not update the last successful URL until processing succeeds.

## v0.11.0
  * HTTP client for RRDP not supports a total-request timeout
  * More metrics for http client through spring boot default metrics

## v0.10.0

  * Add metric for maximum observed object size in repository
  * Check consistency of RRDP snapshot (serial, structure) before processing

## v0.9.1

  * Include git version information in `/actuator/info`
  * Use abbreviated git commit in user-agent string

## v0.9.0
  * Accept (but log) RRDP snapshot when it contains multiple publish entries
    for the same URL.

## v0.8.7

  * Support xsd:base64Binary values surrounded by whitespace
  * Support ASPA objects
  * Upgrade to spring boot 3

## v0.8.6
  * Fix Akamai production RRDP URL

## v0.8.5
  * spring-boot: 2.7.3 -> 2.7.4
  * Monitor Akamai production repository
  * Enable monitoring of rrdp.int.prepdev repository

## v0.8.4
  * Add url label on object count metrics
  * Use records instead of strings as map key for metrics

## v0.8.3

  * Fix rsync URLs so that they work with rsync 3.2.4
  * Add Akamai test repository location
  * Remove I3d repository locations
  * Dependency updates

## v0.8.2

  * Fix core api-key config

## v0.8.1

  * Fix enabling core repository synchronization

## v0.8.0

  * rsync: move hard-coded directories to sync to configuration
  * core: allow to enable/disable in configuration
  * add PAAS environment configurations
  * introduce metrics per object type

## v0.7.4

  * Performance improvements

## v0.7.3

  * Prevent object re-creation when repeatedly disposing the same object.
  * Atomically update object map
  * Run tests in parallel

## v0.7.2

  * Remove dev environment
  * Add metrics for fetcher updates + RRDP serial
  * Intern strings in an attempt to save memory.

## v0.7.1

  * Fix AWS Beanstalk RRDP repository configuration

## v0.7.0

  * Prevent overlapping runs of collectors

## v0.6.3

  * Spring boot: 2.6.5 -> 2.6.6
  * Spring4Shell: remove data-binder mitigation

## v0.6.2

  * Spring framework: force use of 5.3.18

## v0.6.1

  * Use stricter timeouts for RRDP repositories
  * Spring boot: 2.6.4 -> 2.6.5
  * Spring4Shell mitigation
  * Gradle plugin updates for: jib, git-version

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
