# Multi-stage build for the Sakar Cloud Backend (Master Requirements Part 8:
# Docker packaging/deployment, consistent dev/staging/production environments).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src/ src/
RUN ./mvnw -q -B clean package -DskipTests

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN useradd --system --create-home sakar
# Pre-create the map-storage mount point owned by the app user — a named
# volume first-mounted onto a path that doesn't yet exist in the image is
# otherwise created root-owned, which the sakar user could not write to.
RUN mkdir -p /app/data/maps && chown -R sakar:sakar /app/data
USER sakar
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
