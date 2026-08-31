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
COPY modules/retirement/pom.xml modules/retirement/pom.xml
COPY integrations/pom.xml integrations/pom.xml
COPY test-support/pom.xml test-support/pom.xml
COPY adapters/web-ui/pom.xml adapters/web-ui/pom.xml
COPY app/pom.xml app/pom.xml
RUN ./mvnw -B -DskipTests dependency:go-offline

COPY modules modules
COPY integrations integrations
COPY test-support test-support
COPY adapters adapters
COPY app app
RUN ./mvnw -B -DskipTests "-Dapp.build.commit=${GIT_COMMIT}" clean package

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /workspace/app/target/app-*.jar /app/app.jar

EXPOSE 8080

# Runtime DB/API credentials are passed as environment variables.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
