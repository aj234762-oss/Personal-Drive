FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8762
HEALTHCHECK --interval=5s --timeout=3s --start-period=10s --retries=20 \
  CMD curl --fail --silent http://localhost:8762/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
