# Multi-stage build: a full JDK + Maven to build the jar, then only a JRE to run it.
# The final image contains no source code, no Maven and no build tools.

# ---- Stage 1: build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy only what dependency resolution needs first. Docker caches this layer, so the (slow) download of
# all dependencies runs again only when pom.xml or the wrapper changes, not on every source change.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

# Now the sources. Tests are skipped here: they run in CI, and the Testcontainers tests would need
# a Docker daemon inside "docker build".
COPY src src
RUN ./mvnw -B -q package -DskipTests \
    && cp target/fxpulse-*.jar app.jar

# ---- Stage 2: run ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as an unprivileged user instead of root.
RUN groupadd --system fxpulse && useradd --system --gid fxpulse fxpulse
COPY --from=build --chown=fxpulse:fxpulse /app/app.jar app.jar
USER fxpulse

# The app listens on ${PORT:8098} (application.yml); hosts like Render set PORT themselves.
EXPOSE 8098

# MaxRAMPercentage: size the heap from the container's memory limit (e.g. 512 MB on a small cloud
# instance) instead of the host's RAM. All settings (DB, Gemini key, CORS) come from env vars at runtime.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
