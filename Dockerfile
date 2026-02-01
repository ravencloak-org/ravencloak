# syntax=docker/dockerfile:1.4

# Build stage
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy Gradle wrapper and build files first (changes less frequently)
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./

# Copy keycloak-spi build file (needed for Gradle to configure multi-module project)
COPY keycloak-spi/build.gradle.kts keycloak-spi/

# Download dependencies for root project only (not keycloak-spi)
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :dependencies --no-daemon

# Copy source code (only root project src)
COPY src/ src/

# Build only the root Spring Boot app (not keycloak-spi)
ARG VERSION=latest
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :bootJar -Pversion=${VERSION} -x test --no-daemon && \
    cp build/libs/*.jar /app.jar

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

# Install curl for healthcheck and create non-root user
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r appgroup && useradd -r -g appgroup appuser

# Copy the built JAR
COPY --from=builder /app.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
