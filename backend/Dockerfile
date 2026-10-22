# ---------- Bước 1: build file jar ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Tải thư viện trước (được cache nếu pom.xml không đổi)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- Bước 2: image chạy (chỉ có JRE) ----------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S vego && adduser -S vego -G vego \
    && mkdir -p /data/uploads && chown -R vego:vego /data
WORKDIR /app
COPY --from=build /src/target/vego-backend.jar app.jar
USER vego
ENV UPLOAD_DIR=/data/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health > /dev/null || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
