# Vercel Container Runtime image for the Spring Boot API.
# The app defaults to the fail-closed production profile. Configure the required
# database/JWT/redirect settings; online payments remain declined until a real gateway exists.
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY . .
RUN bash ./mvnw -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build --chown=spring:spring /workspace/target/ecommerce-backend-0.0.1-SNAPSHOT.jar /app/app.jar

USER spring:spring
# Favor fast startup for Vercel cold starts; JIT can optimize hot paths after startup.
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:TieredStopAtLevel=1"
EXPOSE 80

# Vercel's container runtime routes HTTP traffic to PORT (80 by default).
# Default to prod intentionally; a deployment must explicitly opt into another
# profile instead of silently running with development-only payment behavior.
CMD ["sh", "-c", "exec java -Dserver.port=${PORT:-80} -jar /app/app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}"]
