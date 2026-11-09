FROM node:24-bookworm-slim AS ui
WORKDIR /src/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-21 AS server
WORKDIR /src/backend
COPY backend/ ./
COPY --from=ui /src/frontend/dist/commonground/browser/ src/main/resources/static/
RUN mvn -B clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir /app/data && chown -R 10001:10001 /app
COPY --from=server /src/backend/target/commonground-1.0.0.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
