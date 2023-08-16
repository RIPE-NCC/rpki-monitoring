FROM gradle:jdk17-focal as builder

RUN apt-get update && apt-get install --yes rsync

RUN useradd app
ADD . /app
WORKDIR /app
RUN gradle build --no-daemon \
    && find /app -name 'rpki-monitoring*.jar' -not -name '*plain*' -exec cp {} /app/app.jar \;

FROM eclipse-temurin:17-jdk-focal

ENV TINI_VERSION v0.19.0

ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini /tini
RUN chmod +x /tini
ENTRYPOINT ["/tini", "--"]

RUN useradd app && apt-get update && apt-get install --yes rsync && rm -rf /var/lib/apt/lists/* && mkdir /app
COPY --from=builder /app/app.jar /app/

CMD ["/opt/java/openjdk/bin/java", "--enable-preview", "-jar", "/app/app.jar"]
