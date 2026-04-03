# Etapa 1: Construccion
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Copiar el archivo pom.xml y descargar dependencias
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar el codigo fuente y construir el JAR
COPY src ./src
RUN mvn clean package -DskipTests

# Etapa 2: Ejecucion
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copiar el JAR construido desde la etapa anterior
COPY --from=builder /app/target/*.jar app.jar

# Exponer el puerto
EXPOSE 8080

# Comando para ejecutar la aplicacion
ENTRYPOINT ["java", "-jar", "app.jar"]