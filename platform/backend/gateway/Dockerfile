# syntax=docker/dockerfile:1
# Build stage: compiles the jar. The cache mount keeps ~/.m2 between builds and between services,
# so dependencies are downloaded once, not five times. Tests are skipped here (they need Docker); CI runs them.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q -DskipTests package \
 && cp target/*.jar /build/app.jar

# Run stage: JRE only, non-root user.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S rostra && adduser -S rostra -G rostra
USER rostra
WORKDIR /app
COPY --from=build /build/app.jar app.jar
EXPOSE 8080
# Small heap and the serial GC: seven containers share one laptop.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
ENTRYPOINT ["java", "-jar", "app.jar"]
