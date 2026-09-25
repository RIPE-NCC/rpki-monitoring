FROM gradle:9-jdk25-corretto@sha256:4bfe7bdc2f9303ecaaf746d845fce2ad418429bb9e619a19a4291c6deee37748 as builder

RUN dnf install -y rsync && dnf clean all

RUN useradd app
ADD . /app
WORKDIR /app
COPY src/main/resources/application.yaml build/resources/main/git.properties* src/main/resources/
RUN gradle bootJar --no-daemon \
    && find /app -name 'rpki-monitoring*.jar' -not -name '*plain*' -exec cp {} /app/app.jar \;

FROM eclipse-temurin:25-jre-alpine@sha256:2ca9adf44f5c29d28ecd26cf92d75cc0c66b7f32bfd839a4439e363a8b428af8

RUN apk add tini rsync

RUN adduser -D app
RUN mkdir /app
COPY --from=builder /app/app.jar /app/

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["/opt/java/openjdk/bin/java", "--enable-preview", "-jar", "/app/app.jar"]
