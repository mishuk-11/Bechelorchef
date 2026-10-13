# syntax=docker/dockerfile:1
# Multi-stage build: compile with the Maven wrapper on JDK 21, run on a slim JRE 21 as a non-root user.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src/ src/
RUN ./mvnw -q -B -DskipTests package && cp target/bachelor-chef-api.jar /app.jar

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
USER app
EXPOSE 8080
# Memory settings sized for small free instances (512 MB): heap ≤ 70 % of the container, the low-overhead
# Serial GC, smaller thread stacks and code cache, and C1-only JIT for a faster, lighter start-up.
# Override JAVA_TOOL_OPTIONS on bigger hosts if you want throughput instead.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=64m -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"
# Health: configure the host's HTTP health check on /actuator/health/readiness (see docs/deployment.md).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
