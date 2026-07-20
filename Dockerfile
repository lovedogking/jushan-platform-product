# =============================================================================
# 智慧停车 SaaS 平台 — 后端 Spring Boot 容器镜像
# =============================================================================
# 使用方式：
#   1. 先在宿主机构建 JAR：mvn clean package -pl parking-boot -am -DskipTests
#   2. 构建镜像：docker build -t jushan-platform:latest .
#   3. 或直接 docker compose -f docker-compose.prod.yml up --build
# =============================================================================

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY parking-boot/target/parking-boot-*.jar app.jar

RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
    CMD wget -q -O- http://localhost:8080/actuator/health/liveness || exit 1

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]
