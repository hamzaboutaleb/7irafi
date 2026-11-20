FROM maven:3.9.12-eclipse-temurin-21-alpine

WORKDIR /shared-kernel
COPY shared-kernel/pom.xml .
COPY shared-kernel/src src
RUN mvn -q -DskipTests install

WORKDIR /app
COPY job-service/pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY job-service/src src
RUN mvn -q -DskipTests package

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "target/job-service-1.0.0.jar"]
