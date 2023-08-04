FROM gradle:jdk17-focal as builder

RUN useradd app
ADD . /app
WORKDIR /app
RUN apt-get update && apt-get install --yes rsync && gradle build --no-daemon && find /app -name rpki-monitoring\*.jar -not -name \*plain\* -exec cp {} /app/app.jar \;

FROM eclipse-temurin:17-jdk-focal
RUN useradd app && apt-get update && apt-get install --yes rsync && rm -rf /var/lib/apt/lists/* && mkdir /app
COPY --from=builder /app/app.jar /app/

ENTRYPOINT /opt/java/openjdk/bin/java --enable-preview -jar /app/app.jar
