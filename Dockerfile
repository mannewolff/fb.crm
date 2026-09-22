# syntax=docker/dockerfile:1
#
# fb.crm — Container-Image in drei Stufen (Issue #32).
#
#   docker build -t fbcrm .
#
# Kopiert wird jede Stufe Pfad fuer Pfad statt `COPY . .`: So gelangt weder eine lokale .env
# noch node_modules oder target/ ins Image, auch ohne .dockerignore.

# --- 1. Frontend ---------------------------------------------------------------------------------
# Dieselbe Node-Version wie das frontend-maven-plugin (pom.xml, node.version).
FROM node:22.11.0-bookworm-slim AS frontend
WORKDIR /build/frontend
# Erst die Manifeste, dann der Rest: Solange sich package-lock.json nicht aendert, bleibt die
# Schicht mit `npm ci` im Cache.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# --- 2. Backend ----------------------------------------------------------------------------------
# Das Frontend ist schon gebaut: -Dskip.frontend=true schaltet das frontend-maven-plugin und die
# Kopie aus frontend/dist ab, das dist aus Stufe 1 liegt stattdessen unter
# src/main/resources/static und geht als gewoehnliche Ressource ins JAR. Node wird so nur einmal
# gebraucht. Die Tests laufen hier nicht — sie sind das Gate vor dem Commit (mvn verify), nicht
# das des Image-Baus; die Testcontainers-ITs braeuchten ausserdem Docker im Build.
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /build
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
COPY src/ src/
COPY --from=frontend /build/frontend/dist/ src/main/resources/static/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -Dskip.frontend=true -DskipTests package \
    && cp target/fb-crm-*.jar /build/app.jar

# --- 3. Laufzeit ---------------------------------------------------------------------------------
FROM eclipse-temurin:25-jre
# curl allein fuer den Healthcheck in docker-compose.yml.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
# Ein eigener Systemnutzer ohne Login und ohne Heimatverzeichnis: Die Anwendung braucht keine
# Rechte ueber ihr JAR hinaus, und ein Einbruch in den Prozess ist dann kein root im Container.
RUN groupadd --system fbcrm && useradd --system --gid fbcrm --no-create-home --shell /usr/sbin/nologin fbcrm
WORKDIR /app
COPY --from=backend /build/app.jar app.jar
USER fbcrm
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
