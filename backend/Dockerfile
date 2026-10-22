# 多阶段构建：第一阶段用 Maven 编译打包，第二阶段只带 JRE 和 jar，镜像小很多
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 先只拷 pom 下载依赖：源码变化时这一层能命中缓存，不用每次重新下载
COPY pom.xml .
COPY code-nest-api/pom.xml code-nest-api/
COPY code-nest-core/pom.xml code-nest-core/
COPY code-nest-service/pom.xml code-nest-service/
COPY code-nest-web/pom.xml code-nest-web/
RUN mvn -B -q dependency:go-offline -DskipTests || true
COPY . .
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
COPY --from=build /build/code-nest-web/target/app.jar app.jar
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
