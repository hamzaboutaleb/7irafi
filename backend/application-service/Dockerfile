FROM maven:3.9.12-eclipse-temurin-21-alpine

WORKDIR /shared-kernel
COPY shared-kernel/pom.xml .
COPY shared-kernel/src src
RUN mvn -q -DskipTests install

WORKDIR /app
COPY application-service/pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY application-service/src src
RUN mvn -q -DskipTests package

EXPOSE 8084
ENTRYPOINT ["java", "-jar", "target/application-service-1.0.0.jar"]
