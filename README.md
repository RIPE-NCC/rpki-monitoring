# rpki-monitoring

### Running it:
```
gradle bootRun
# Or with remote debugging (5005):
gradle bootRun --debug-jvm
# Select a profile and set needed settings:
SPRING_PROFILES_ACTIVE=prepdev gradle bootRun
# Or
SPRING_PROFILES_ACIVE=local gradle clean bootRun
```

#### With Docker
```
# Build thin image, without explicitly creating a Dockerfile with multi-stage build
gradle jibDockerBuild
# Or specify a base image
gradle jibDockerBuild -Pjib.from.image=[image name]
# And run it
docker run -p 9090:9090 --rm docker-registry.ripe.net/rpki/rpki-monitoring
# Or for a profile that requires environment variables:
docker run \
	-e SPRING_PROFILES_ACTIVE=prepdev \
	-p 9090:9090 \
	--rm docker-registry.ripe.net/rpki/rpki-monitoring
```

The base image for the CI build is set through environment variables.
```
    - gradle jib -Pjib.from.image=$DOCKER_IMAGE --image=$CI_REGISTRY_IMAGE:latest
    - gradle jib -Pjib.from.image=$DOCKER_IMAGE_DEBUG --image=$CI_REGISTRY_IMAGE:debug
```
