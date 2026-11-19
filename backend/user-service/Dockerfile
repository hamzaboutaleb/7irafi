FROM maven:3.9.12-eclipse-temurin-21-alpine

WORKDIR /shared-kernel
COPY shared-kernel/pom.xml .
COPY shared-kernel/src src
RUN mvn -q -DskipTests install

WORKDIR /app
COPY user-service/pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY user-service/src src
RUN mvn -q -DskipTests package

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "target/user-service-0.0.1-SNAPSHOT.jar"]
