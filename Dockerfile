FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp verify
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir .data && chown -R 10001:10001 /app
COPY --from=build /app/target/support-request-api-1.0.0.jar app.jar
USER 10001
ENV HOST=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
