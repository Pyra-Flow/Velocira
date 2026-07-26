FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system velocira && useradd --system --gid velocira velocira
COPY --from=build /workspace/target/backend-*.jar /app/velocira.jar
USER velocira
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/velocira.jar"]
