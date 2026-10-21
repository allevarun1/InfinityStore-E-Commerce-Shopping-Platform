# Portable build for any container host (Render, Fly.io, Koyeb, a VPS). Railway does not need it.
# Not built in the repository's CI: verify with `docker build` before relying on it.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 1001 shop
COPY --from=build /app/target/ecommerce-backend-*.jar app.jar
USER shop
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
