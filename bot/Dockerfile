# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle /src/
COPY gradle /src/gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies
COPY src /src/src
RUN ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:21-jre AS runtime
# docker-cli is needed so the bot can `docker exec` into the vpn-russia
# container to provision/rotate/revoke VPN users via the in-container `vpn` CLI.
# The host's /var/run/docker.sock is mounted by compose.
RUN apt-get update && apt-get install -y --no-install-recommends curl docker.io \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/build/libs/*.jar /app/app.jar
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
