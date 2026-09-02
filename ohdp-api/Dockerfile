FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S bhfyzx && adduser -S -G bhfyzx bhfyzx

WORKDIR /app
COPY --from=build /workspace/target/ohdp-api-*.jar /app/app.jar
RUN mkdir -p /data/reports /data/radiology /data/reference-documents \
    && chown -R bhfyzx:bhfyzx /app /data

USER bhfyzx
EXPOSE 18080

HEALTHCHECK --interval=15s --timeout=5s CMD wget -q --spider http://127.0.0.1:18080/v3/api-docs || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
