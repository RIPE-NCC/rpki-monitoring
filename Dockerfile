FROM gradle:9-jdk25-corretto@sha256:0e3ac12fa0bcc1888c943e8bca04c7cb7d59f2082c2aadb55617df3ba14daef2 as builder

RUN dnf install -y rsync && dnf clean all

RUN useradd app
ADD . /app
WORKDIR /app
COPY src/main/resources/application.yaml build/resources/main/git.properties* src/main/resources/
RUN gradle bootJar --no-daemon \
    && find /app -name 'rpki-monitoring*.jar' -not -name '*plain*' -exec cp {} /app/app.jar \;

FROM eclipse-temurin:25-jre-alpine@sha256:b7528962e65d43064d8a8b67ba175367896bc948363ea2d3fcd7f10d34e1f048

RUN apk add tini rsync

RUN adduser -D app
RUN mkdir /app
COPY --from=builder /app/app.jar /app/

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["/opt/java/openjdk/bin/java", "--enable-preview", "-jar", "/app/app.jar"]
