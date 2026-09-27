# ---------- 构建阶段：Maven + JDK 21 ----------
# 前端产物已提交在 src/main/resources/static（npm run build 输出），
# 因此容器内构建无需 Node 环境，一条 Dockerfile 搞定。
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 先单独 COPY pom.xml 并预下载依赖：pom 不变时命中 Docker 层缓存，改代码重建只需秒级
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B -DskipTests package

# ---------- 运行阶段：只带 JRE，镜像更小更安全 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# H2 文件库 / 头像等运行时数据挂这个卷（docker-compose 已映射到宿主机 ./data）
VOLUME /app/data

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Dfile.encoding=UTF-8"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
