# ---- Стадия сборки ----
FROM gradle:8.11-jdk21-alpine AS build
WORKDIR /app
COPY --chown=gradle:gradle . .
RUN gradle bootJar --no-daemon -x test

# ---- Финальный образ ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S bookmeta && adduser -S bookmeta -G bookmeta
COPY --from=build /app/build/libs/*.jar app.jar
RUN chown bookmeta:bookmeta app.jar
USER bookmeta

EXPOSE 8081
ENTRYPOINT ["java", "-Xms256m", "-Xmx768m", "-XX:+UseSerialGC", "-jar", "app.jar"]