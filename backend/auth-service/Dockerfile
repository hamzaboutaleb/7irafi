FROM maven:3.9.12-eclipse-temurin-21-alpine

WORKDIR /shared-kernel
COPY shared-kernel/pom.xml .
COPY shared-kernel/src src
RUN mvn -q -DskipTests install

WORKDIR /app
COPY auth-service/pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY auth-service/src src
RUN mvn -q -DskipTests package

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "target/auth-service-0.0.1-SNAPSHOT.jar"]
