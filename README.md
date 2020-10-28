# rpki-monitoring

RPKI monitoring is deployed through gitlab as a docker container. It is available
on `uncore-{1,2}.rpki.(environment?).ripe.net:9090`.

### Endpoints:

  * Difference in published objects: `/published-object-diffs`
  * Objects that are about to expire in `in_hours=...` hours:
    * rsync: `/about_to_expire/rsync?in_hours=12`
    * rrdp: `/about_to_expire/rrdp?in_hours=12`


### Running it:
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
# And run it
docker run -p 9090:9090 --rm docker-registry.ripe.net/rpki/rpki-monitoring
# Or for a profile that requires environment variables:
docker run \
	-e SPRING_PROFILES_ACTIVE=production \
	-e CORE_API_KEY=${RPKI_CORE_API_KEY} \
	-p 9090:9090 \
	--rm docker-registry.ripe.net/rpki/rpki-monitoring
```

The base image for the CI build is set through environment variables.
```
    - gradle jib -Pjib.from.image=$DOCKER_IMAGE --image=$CI_REGISTRY_IMAGE:latest
    - gradle jib -Pjib.from.image=$DOCKER_IMAGE_DEBUG --image=$CI_REGISTRY_IMAGE:debug
```
