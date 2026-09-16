# Two stages: build with a JDK, ship with a JRE. The final image carries no compiler, no
# Maven, no source and no build cache, which keeps it small and removes tools an attacker
# would otherwise find already installed.

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Dependencies are resolved from the POM alone, before any source is copied, so this layer
# is cached and rebuilt only when the POM changes. Editing a Java file then costs a compile
# rather than a re-download of every dependency.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
# Tests are deliberately skipped here. The integration tests need a Docker daemon of their
# own, and this build is already running inside one. `./mvnw verify` runs on the CI host,
# which is the right place for it - see .github/workflows/ci.yml.
RUN ./mvnw -B -DskipTests package


FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Runs as an unprivileged user: nothing this process does needs root, and a container that
# never needs it should never have it.
RUN addgroup -S engine && adduser -S -G engine engine

COPY --from=build /build/target/matching-engine-*.jar app.jar
USER engine

EXPOSE 8080

# Reports unhealthy until Flyway has migrated and the datasource answers, so an orchestrator
# does not route traffic to an instance that cannot yet serve it.
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
