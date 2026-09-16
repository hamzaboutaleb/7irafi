FROM maven:3.9.12-eclipse-temurin-21-alpine

WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src src
RUN mvn -q -DskipTests package

EXPOSE 8084
ENTRYPOINT ["java", "-jar", "target/application-service-1.0.0.jar"]
