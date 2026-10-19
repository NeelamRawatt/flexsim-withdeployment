# =====================================================================
# Dockerfile for auth-service  (a Dockerfile = a recipe that builds a Docker IMAGE).
# An IMAGE is a sealed package: your program + Java + everything it needs.
# Anyone (your laptop, CI, Azure, Kubernetes) can run it the same way.
#
# TWO STAGES (multi-stage build):
#   Stage 1 "build": has the full JDK and Maven. It builds the .jar file.
#   Stage 2 "run":   has only a small Java runtime and the .jar.
# Why? The final image stays small and has no build tools or source code
# (fewer things for an attacker to use, and faster to download).
# =====================================================================

# ---------- Stage 1: build the .jar ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Copy the Maven wrapper and pom.xml first. Docker saves each step ("layer"),
# so if only your code changes, it does not repeat the earlier steps.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Windows saves text files with CRLF line endings. Linux cannot run a script like that.
# This line removes the extra characters, so ./mvnw works inside the image.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

COPY src/ src/
# The cache mount keeps downloaded libraries between builds (the next build is much faster).
# -DskipTests: the tests run in CI BEFORE we build the image. -B = plain log output.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests

# ---------- Stage 2: run it ----------
FROM eclipse-temurin:21-jre
# curl is used by the health check (Docker asks "are you healthy?" with it).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build /build/target/*.jar /app/app.jar

# Never run as "root" inside a container. If someone breaks into the program,
# a normal user can do much less damage.
USER app

ENV TZ=UTC
EXPOSE 8081

# MaxRAMPercentage: the program may use up to 75% of the memory the container is given
# (important in Kubernetes, where memory is limited). ExitOnOutOfMemoryError: if memory
# runs out, stop at once so Docker/Kubernetes starts a fresh copy.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]