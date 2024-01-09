# rpki-monitoring

rpki-monitoring is a tool for RPKI CA and repository operators. The development
of rpki-monitoring at the RIPE NCC was motivated by an incident where RPKI
objects expired. After covering this initial gap, more data for _prospective_
alerts for RPKI operators was added.

This is a valuable component for monitoring RPKI repositories, especially when
the infrastructure gets complicated (multiple locations for RRDP repositories,
multiple rsync nodes, etc).

rpki-monitoring fetches data from (multiple) rsync or RRDP repositories and
potentially an API endpoint providing a ground truth (for example, the state of
a CA system).

It then creates metrics for:
  * When the first objects are about to expire (both for all objects and for
    paths matching a regex).
  * The number of objects "created" in the last [time window].
  * The number of objects in (repository) `x` that are not in `y`.
  * [optionally]: The number of certificates that have overlapping IP resources.
  * In turn, these metrics are the basis for alerts on:
  * Consistency: Check that objects in the CA system are eventually in repositories.
  * Consistency: Check that all repositories contain the same objects.
  * Liveliness: Check that objects are created.
  * Liveliness: Check that objects are not about to expire (in general).
  * Liveliness: Check that objects from the offline-signing process are not about to expire.
  * Correctness: Check that certificates do not overlap for an extended period.


## License

Copyright (c) 2020-2024 RIPE NCC
All rights reserved.

This software, including all its separate source codes, is licensed under the
terms of the BSD 3-Clause License. If a copy of the license was not distributed
to you, you can obtain one at https://github.com/RIPE-NCC/rpki-monitoring/blob/main/LICENSE.txt

## Deployment

The preferred way to run rpki-monitoring is in a docker container. This docker
container contains java and rsync.

**Resources required**
rpki-monitoring is a multi-threaded java application. The resource usage
depends on both the size of the repositories monitored, as well as the size of
each repository. To monitor the production repository infrastructure of the
RIPE NCC rpki-monitoring needs 16GB of memory.


```
$ ./gradlew generateGitProperties
$ docker build . -t rpki-monitoring-main
...
 ---> 637dd34a2284
Successfully built 637dd34a2284
Successfully tagged rpki-monitoring-main:latest
# note: using the tag here, alternatively, use the hash
$ docker run --rm -e CORE_ENABLE=false -p 9090:9090 rpki-monitoring-main:latest
...
application startup
...
```

**The main endpoint** is `/actuator/prometheus`.

Optionally, use `JAVA_TOOL_OPTIONS` to provide JVM arguments such as
`JAVA_TOOL_OPTIONS="-Xmx16128M` to adjust the amount of memory available.

To adjust the configuration, either change `application.yaml` and rebuild. Or,
  1. mount a volume with an additional yaml file with configuration,
  2. set the `SPRING_CONFIG_ADDITIONAL_LOCATION=file:/[path in container.(properties|yaml)`
  3. and set `SPRING_PROFILES_ACTIVE=[profile name]`.
  4. optionally set `JAVA_TOOL_OPTIONS`

The file in `src/main/resources/application.yaml` is the default configuration
and shows the available options.

## Endpoints

### Metrics

`/actuator/prometheus` contains a prometheus endpoint.

### Differences

__Difference in published objects__

```
/published-object-diffs
```

This runs the differences between all trackers and updates the metrics.

__Difference between 2 repositories__

```
/diff?lhs=<repo1>&rhs=<repo2>&threshold=0
```

The `threshold` is in seconds.

### Expiration

List all objects that are about to expire in `in_hours=...` hours:

* rsync: `/about_to_expire/rsync?in_hours=12`
* rrdp: `/about_to_expire/rrdp?in_hours=12`

### Repositories

__Information__

View the URL, type and size (at `threshold` seconds from now) for a repository
named `<repo>`.

```
/<repo>/info?theshold=0
```

__Inspect objects__

Inspect objects with given URI in the repository tracker. This allows to inspect
timings `first-seen` and `disposed-at` in the tracked objects. Therefore it does
not take a threshold.

```
/<repo>/inspect?uri=<uri>
```

As `<uri>` must be encoded, an easy way of calling this with `curl` is to set
`data-urlencode` with a `GET` request. For example:

```
> curl -G 'http://localhost:9090/core/inspect' --data-urlencode 'uri=<uri>'
```

__List objects__


List objects with given URI in the repository at `threshold` seconds from now.

```
/<repo>/objects?uri=<uri>&threshold=0
```

See above for convenient URL encoding the `<uri>` parameter.

### Running it

```
gradle bootRun
# Or with remote debugging (5005):
gradle bootRun --debug-jvm
# Select a profile and set needed settings through an environment var
# CORE_API_KEY: Only used when combined with https://github.com/RIPE-NCC/rpki-core
export CORE_API_KEY=$(cat ~/src/ripe-portal/conf/application.production.conf | grep authorisation-service | sed -e "s/.*= \"\(.*\)\"/\1/")
SPRING_PROFILES_ACTIVE=production gradle bootRun
# Or
CORE_ENABLE=false SPRING_PROFILES_ACTIVE=local gradle clean bootRun
```

#### With Docker

```
# Or build with Dockerfile that does not rely on base image
docker build . -t rpki-monitoring
# And run it
docker run -p 9090:9090 --rm rpki-monitoring
# Or for a profile that requires environment variables:
docker run \
    -it \
    --name rpki-monitoring \
    -v $(pwd):/export/ \
    -e JAVA_TOOL_OPTIONS="-Xmx16128M -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/export/dump.hprof -XX:+ExitOnOutOfMemoryError" \
    -e SPRING_PROFILES_ACTIVE=production \
    -e CORE_ENABLE=false \
    -p 9090:9090 \
    --rm rpki-monitoring

# or, with access to a rpki-core instance, add:
    -e CORE_API_KEY=${RPKI_CORE_API_KEY} \
```
