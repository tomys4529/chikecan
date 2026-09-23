# chikecan 本番用マルチステージビルド
# React(frontend)とSpring Boot(backend)を1つの実行イメージへまとめ、
# Spring BootがReactの静的ファイルを配信することで、フロントとAPIを同一Originにする。

# ---- Stage 1: フロントエンドをビルド ----
# Node 24 LTS: package.jsonのViteがengines "node": "^20.19.0 || >=22.12.0" を要求しており、
# CLAUDE.mdで採用しているNode 24 LTSと互換性がある。
FROM node:24-slim AS frontend-build
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---- Stage 2: バックエンドをビルド(frontend/distを組み込んでjar化) ----
FROM eclipse-temurin:21-jdk AS backend-build
WORKDIR /app/backend
COPY backend/mvnw ./
COPY backend/.mvn ./.mvn
COPY backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=frontend-build /app/frontend/dist ./src/main/resources/static
RUN ./mvnw -q -B package -DskipTests

# ---- Stage 3: 実行用(JREのみ、軽量) ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend-build /app/backend/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
