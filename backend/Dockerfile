# =======================================================================
# Multi-stage Dockerfile for the SRoadTutor Spring Boot backend.
# Stage 1 compiles the fat JAR using the Maven wrapper.
# Stage 2 copies only the JAR into a slim JRE image for runtime.
# =======================================================================

# ---------- Stage 1: build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 1. Copy only the files needed to resolve dependencies first.
#    This lets Docker cache the expensive `mvnw dependency:go-offline` step
#    when only source files change between builds.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline -q

# 2. Now copy source and build. Tests are skipped here — CI runs them.
COPY src/ src/
RUN ./mvnw -B -DskipTests package -q

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Add a non-root user for safety.
RUN groupadd -r app && useradd -r -g app app
USER app

# Railway injects $PORT; our application.yml reads it via ${PORT:8080}.
ENV SERVER_PORT=8080
EXPOSE 8080

# Common JVM tuning for small Railway instances (~512 MB RAM).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"

COPY --from=build /workspace/target/sroadtutor-backend.jar /app/app.jar

# Health check uses Spring Actuator.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:${SERVER_PORT}/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
