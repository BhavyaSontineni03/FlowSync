# Multi-stage build that does not depend on a checked-in Maven wrapper.
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /build
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline
# The POM deliberately resolves the contract as ../proto from /build.
COPY proto /proto
COPY backend/src ./src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/expense-management-platform-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
