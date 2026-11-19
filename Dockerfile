# Etapa 1: Compilacion con Maven y Java 21
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

# Etapa 2: Imagen de ejecucion con JRE 21
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

ENV PORT=8081
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
