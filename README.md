# rpki-monitoring

RPKI monitoring is deployed through gitlab as a docker container. It is available
on `uncore-{1,2}.rpki.(environment?).ripe.net:9090`.

## Endpoints

### Differences

__Difference in published objects__

```
/published-object-diffs
```

This runs the differences between all trackers and update the metrics.

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
> curl -G 'http://uncore-1.rpki.ripe.net:9090/core/inspect' --data-urlencode 'uri=<uri>'
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
export CORE_API_KEY=$(cat ~/src/ripe-portal/conf/application.production.conf | grep authorisation-service | sed -e "s/.*= \"\(.*\)\"/\1/")
SPRING_PROFILES_ACTIVE=production gradle bootRun
# Or
SPRING_PROFILES_ACIVE=local gradle clean bootRun
```

#### With Docker

```
# Build thin image, without explicitly creating a Dockerfile with multi-stage build
gradle jibDockerBuild
# Or build with Dockerfile that does not rely on base image
docker build .
# And run it
docker run -p 9090:9090 --rm docker-registry.ripe.net/rpki/rpki-monitoring
# Or for a profile that requires environment variables:
docker run \
	-e SPRING_PROFILES_ACTIVE=production \
	-e CORE_ENABLE=false \
	# or, with access to a rpki-core instance:
	-e CORE_API_KEY=${RPKI_CORE_API_KEY} \
	-p 9090:9090 \
	--rm docker-registry.ripe.net/rpki/rpki-monitoring
```

The base image for the CI build is set through environment variables.
```
    - gradle jib -Pjib.from.image=$DOCKER_IMAGE --image=$CI_REGISTRY_IMAGE:latest
    - gradle jib -Pjib.from.image=$DOCKER_IMAGE_DEBUG --image=$CI_REGISTRY_IMAGE:debug
```
