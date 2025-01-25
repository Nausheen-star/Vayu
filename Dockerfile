FROM openjdk:21-jdk-slim

LABEL authors="ajaynegi"

WORKDIR /app

COPY target/vayu-1.0-SNAPSHOT.jar /app/vayu-webserver.jar

EXPOSE 8080


CMD ["java", "-jar", "/app/vayu-webserver.jar"]
