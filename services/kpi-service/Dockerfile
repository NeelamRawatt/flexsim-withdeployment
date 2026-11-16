# =====================================================================
# Dockerfile for kpi-service (a recipe that builds a Docker IMAGE = the program + Java, sealed).
# Same two stages as the other services:
#   Stage 1 "build": JDK + Maven build the .jar
#   Stage 2 "run":   only a small Java runtime + the .jar (smaller, no build tools inside)
# Difference: this service has no Maven wrapper (mvnw), so stage 1 uses the official Maven image.
# =====================================================================

# ---------- Stage 1: build the .jar ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src/ src/
# The cache mount keeps downloaded libraries between builds. Tests run in CI before this.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# ---------- Stage 2: run it ----------
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build /build/target/*.jar /app/app.jar

# Never run as "root" inside a container.
USER app

ENV TZ=UTC
EXPOSE 8084

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]
