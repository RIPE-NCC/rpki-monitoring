FROM gradle:9-jdk25-corretto@sha256:d82835e0dbf3234c4bea2a1d8d5db282d5e032257324f48290caa76e6de0d025 as builder

RUN dnf install -y rsync && dnf clean all

RUN useradd app
ADD . /app
WORKDIR /app
COPY src/main/resources/application.yaml build/resources/main/git.properties* src/main/resources/
RUN gradle bootJar --no-daemon \
    && find /app -name 'rpki-monitoring*.jar' -not -name '*plain*' -exec cp {} /app/app.jar \;

FROM eclipse-temurin:25-jre-alpine@sha256:3137541deb3cac6626b5d9a4a2187bc0d6a34312f858bd2c67dd01e732e6b682

RUN apk add tini rsync

RUN adduser -D app
RUN mkdir /app
COPY --from=builder /app/app.jar /app/

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["/opt/java/openjdk/bin/java", "--enable-preview", "-jar", "/app/app.jar"]
