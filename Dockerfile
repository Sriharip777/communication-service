FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

ARG JAR_FILE=target/communication-service-1.0.0.jar
COPY ${JAR_FILE} app.jar

ENV JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 8084

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
