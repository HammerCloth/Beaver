# syntax=docker/dockerfile:1
# 编译阶段固定在构建机的原生架构上跑：jar 与平台无关，多架构镜像只需换运行时底包，不必在模拟器里编译
FROM --platform=$BUILDPLATFORM eclipse-temurin:26-jdk-alpine AS build
WORKDIR /app
# 先只拷依赖描述，依赖下载单独成层，源码改动时不必重新下载
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package

FROM eclipse-temurin:26-jre-alpine
LABEL dev.beaver.component="backend"
WORKDIR /app
ENV JAVA_OPTS="-Xmx256m -Xms64m"
COPY --from=build /app/target/zero-backend-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
