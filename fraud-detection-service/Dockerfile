# Multi-stage build for minimal image size
FROM gradle:8.5-jdk21-alpine AS build

WORKDIR /app

# Copy Gradle files for dependency caching
COPY settings.gradle build.gradle gradle.properties ./
COPY fraud-detection-service/build.gradle ./fraud-detection-service/

# Download dependencies (cached layer)
RUN gradle fraud-detection-service:dependencies --no-daemon || true

# Copy source code
COPY fraud-detection-service/src ./fraud-detection-service/src

# Build the application
RUN gradle fraud-detection-service:bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Add non-root user for security
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser

# Copy JAR from build stage
COPY --from=build /app/fraud-detection-service/build/libs/fraud-detection-service.jar app.jar

# Change ownership
RUN chown -R appuser:appuser /app

USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]

