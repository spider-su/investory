# syntax=docker/dockerfile:1

FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace
ARG GIT_COMMIT=

COPY pom.xml ./
COPY .mvn ./.mvn
COPY --chmod=755 mvnw ./
COPY mvnw.cmd ./
RUN ./mvnw -B -DskipTests dependency:go-offline

COPY src ./src
RUN ./mvnw -B -DskipTests "-Dapp.build.commit=${GIT_COMMIT}" clean package

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /workspace/target/*.jar /app/app.jar

EXPOSE 8080

# Runtime DB/API credentials are passed as environment variables.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
