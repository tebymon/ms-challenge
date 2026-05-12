# ─────────────────────────────────────────────────────────────
# STAGE 1: Build
# ─────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copiar primero el pom.xml para aprovechar el caché de capas de Docker
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -q 2>/dev/null || true

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -q

# ─────────────────────────────────────────────────────────────
# STAGE 2: Runtime
# Usamos JRE (más liviano que JDK) para producción
# ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Usuario no-root por seguridad
RUN addgroup -S appuser && adduser -S appuser -G appuser
USER appuser

COPY --from=builder /app/target/*.jar app.jar

# JVM tuning para contenedores:
#   -XX:+UseContainerSupport     respeta los límites de memoria del contenedor
#   -XX:MaxRAMPercentage=75.0    usa máximo el 75% de la RAM del contenedor
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1
