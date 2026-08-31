# syntax=docker/dockerfile:1

FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace
ARG GIT_COMMIT=

COPY pom.xml ./
COPY .mvn ./.mvn
COPY --chmod=755 mvnw ./
COPY mvnw.cmd ./
COPY modules/shared/pom.xml modules/shared/pom.xml
COPY modules/investment/pom.xml modules/investment/pom.xml
COPY modules/longterm/pom.xml modules/longterm/pom.xml
COPY modules/profile/pom.xml modules/profile/pom.xml
COPY modules/retirement/pom.xml modules/retirement/pom.xml
COPY integrations/pom.xml integrations/pom.xml
COPY test-support/pom.xml test-support/pom.xml
COPY adapters/web-ui/pom.xml adapters/web-ui/pom.xml
COPY app/pom.xml app/pom.xml
RUN sed -i 's/\r$//' mvnw \
    && ./mvnw -B -DskipTests dependency:go-offline

COPY modules modules
COPY integrations integrations
COPY test-support test-support
COPY adapters adapters
COPY app app
COPY docs/quality/ui-baselines docs/quality/ui-baselines
RUN ./mvnw -B "-Dapp.build.commit=${GIT_COMMIT}" clean package

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/app/target/app-*.jar /app/app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:8080/actuator/health || exit 1

# Runtime DB/API credentials are passed as environment variables.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
