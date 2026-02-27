FROM eclipse-temurin:25-jdk AS builder

WORKDIR /app
#Dependency caching
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts build.gradle.kts
COPY settings.gradle.kts settings.gradle.kts
RUN chmod +x gradlew

RUN ./gradlew dependencies --no-daemon

COPY src src

RUN ./gradlew shadowJar --no-daemon

FROM gcr.io/distroless/java25-debian13

WORKDIR /app

COPY --from=builder /app/build/libs/*-all.jar app.jar

EXPOSE 1910

ENTRYPOINT ["java", "-jar", "/app.jar"]