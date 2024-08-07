## HEAD

## v0.20.0
  * Improve use of RRDP data during integration tests.
  * Monitor RRDP PAAS repositories through CDN locations.

## v0.19.0
  * Use new flag on deploy to update container. Hotfixes a change to internal
    infrastructure that was not communicated by the platform team.

## v0.18.5
  * Gradle configuration is now done in Kotlin.
  * Various configuration changes.

## v0.18.4

  * hotfix: fix path of duplicate all resources CA; after more debugging a different certificate must be ignored.

## v0.18.3

  * hotfix: ignore duplicate all resources CA; to be removed when keyroll is finished.

## v0.18.2

  * hotfix: Use _expiration_ for expiration instead of creation.

## v0.18.1

  * Do not treat v1 ASPA object as parse failures when `rpkimonitor.accept-aspa-v1: true`

## v0.18.0

  * Use new rpki-commons with profile-15 aspa code

## v0.17.3

  * Print the first N overlapping resources and first M URIs of certificates
    that have overlaps.

## v0.17.2

  * Match metric labels between RRDP serial and object metrics

## v0.17.1

  * 14.5 hour threshold before having alerting on top level CA/manifest expiry

## v0.17.0

  * Switch base image to eclipse-temurin based on focal

## v0.16.7

  * Scan RRDP every minute
  * Consolidate rsyncit targets to have a single target behind a load-balancer.

## v0.16.6

  * log the cause when a repo update is aborted, but continue fetching the
    other repositories.

## v0.16.5

  * **hotfix:** parse hex sha256 hashes from core: prevents false positive
    file difference.

## v0.16.4

  * Do not log missing manifests.
  * Add `Dockerfile` that does not rely on NCC internal images

## v0.16.3

  * Raise default thread limit to 8 and share semaphore over types.

## v0.16.2

  * Store hashes as byte arrays instead of interned strings.

## v0.16.1

  * Improve (CPU) performance of certificate analysis by removing resources
    from CertificateEntry hashCode.

## v0.16.0
  * **Fix** memory explosion during certificate comparison with many overlaps.

## v0.15.1

  * Describe HTTP client in line about snapshot download
  * Fix a broken log line
  * Log the hash of downloaded snapshot

## v0.15.0

  * Log the rejected file's content
  * Use three threads when collecting
  * Log the snapshot serial and URL on update

## v0.14.3

  * Add certificate overlap analysis
  * Allow-list specific SIAs to alert on for overlap analysis
  * Allow short-term overlap (keyroll)
  * Limit concurrent fetches to `collector.threads` parallel fetches per repo
    type.

## v0.14.2

  * Add rsyncit targets for production and prepdev
  * Remove beanstalk target from monitoring

## v0.14.1

  * Remove rsync migrate production host from monitoring

## v0.14.0
  * Improve RRDP implementation testability and quality.

## v0.13.3
  * Validate that `session_id` is a UUIDv4
  * spring boot 3.1.1, rpki-commons 1.34, other dependency updates.

## v0.13.2
  * Disable tracing by default and enable only on prepdev

## v0.13.1
  * Add opentelemetry tracing
  * Dependency updates
  * Add Linode external publication server

## v0.13.0
  * Spring boot 3.1
  * Use connect-to in metric tags, to prevent overwriting metrics from
    multiple RRDP sources.

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
