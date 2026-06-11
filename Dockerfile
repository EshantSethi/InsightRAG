# ---- Build stage: compile and package the fat jar on a full JDK 21 + Maven image ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first (cached layer) so source-only changes don't re-download the world.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# Build the application. Tests run in CI, not in the image build, to keep it fast and reproducible.
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---- Runtime stage: ship only the JRE + the jar + the documents to ingest ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# The fat jar bundles the in-process all-MiniLM ONNX model, so no model download at runtime.
COPY --from=build /build/target/insightrag.jar app.jar
# DocumentLoader reads the data directory from the working dir at startup (insightrag.ingestion.data-dir=data).
COPY data ./data

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
