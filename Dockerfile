# Multi-stage build for Smart Dashboard
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Install Maven directly to ensure clean, fast, reliable builds
RUN apk add --no-cache maven

COPY pom.xml ./
# Cache dependencies layer
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

# Production runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "app.jar"]
