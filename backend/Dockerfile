# --- Build stage ------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# Cache deps separately for faster incremental builds.
COPY pom.xml ./
RUN mvn -B -q -e -Dmaven.test.skip=true dependency:go-offline
COPY src ./src
# -Dmaven.test.skip=true skips compiling test sources too; tests run separately via `mvn verify` on the host.
RUN mvn -B -q -e -Dmaven.test.skip=true package

# --- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Alpine doesn't ship curl by default; use wget for the healthcheck.
RUN apk add --no-cache wget
COPY --from=build /workspace/target/shortener.jar /app/shortener.jar
EXPOSE 8080
ENV JAVA_OPTS=""
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=5 \
    CMD wget --spider -q http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/shortener.jar"]
