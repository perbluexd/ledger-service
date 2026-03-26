# ---------- BUILD STAGE ----------
FROM gradle:8.10.2-jdk21 AS build
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
# COPY gradle.properties ./   # si existe

RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew clean bootJar -x test --no-daemon

# ---------- RUNTIME STAGE ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd -r spring && useradd -r -g spring spring

COPY --from=build /app/build/libs/*.jar app.jar

# RUN chown -R spring:spring /app   # si necesitas escritura

USER spring:spring

EXPOSE 8081

ENTRYPOINT ["java","-jar","/app/app.jar"]