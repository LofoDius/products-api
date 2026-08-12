# Build fat JAR inside the image so `docker compose up --build` works without local Gradle.
FROM gradle:8.10.2-jdk21-alpine AS build
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle . .
RUN gradle bootJar --no-daemon -x test

FROM amazoncorretto:21-alpine-jdk
WORKDIR /app
COPY --from=build /home/gradle/project/build/libs/products-api-1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
