# ==============================================================================
# NEXUSMAP Backend Multi-Stage Production Dockerfile
# Base: Eclipse Temurin OpenJDK 21
# ==============================================================================

# Stage 1: Dependency resolution and compilation
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# Cache Maven dependencies layer
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Compile and package application
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Hardened Alpine JRE runtime
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Run as non-privileged security user
RUN addgroup -S nexusgroup && adduser -S nexususer -G nexusgroup
USER nexususer:nexusgroup

# Copy executable jar artifact from builder
COPY --from=builder /workspace/target/*.jar /app/app.jar

EXPOSE 8080

# Health check using Spring Boot Actuator probe
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/api/v1/health || exit 1

# Containerized JVM tuning
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]
