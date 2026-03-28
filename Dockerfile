# ================================
# Builder stage
# ================================
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper & pom (for dependency caching)
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw

# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B -q

# Copy source and build
COPY src ./src
RUN ./mvnw clean package -DskipTests=true -B -q


# ================================
# Runtime stage                   ← named so --target works
# ================================
FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app

# Security: non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy only the JAR from builder
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8084

# Fixed: port 8080 not 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8084/actuator/health || exit 1

ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]