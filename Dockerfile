FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl tar \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/target/product-auth-api-0.0.1-SNAPSHOT.jar app.jar
COPY start-api.sh /app/start-api.sh
RUN chmod 755 /app/start-api.sh

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["/app/start-api.sh"]
