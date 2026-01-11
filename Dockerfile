# 1. 基础镜像
FROM openjdk:8-jdk-alpine

# 2. 维护者
LABEL maintainer="heima-student"

# 3. 设定时区 (修复点：先安装 tzdata，再设置时区，最后清理缓存减小体积)
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories && \
    apk add --no-cache tzdata && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 4. 工作目录
WORKDIR /app

# 5. 拷贝 Jar 包 (确保你的 pom.xml 没有配置 finalName，或者是这个名字)
COPY target/hm-dianping-0.0.1-SNAPSHOT.jar app.jar

# 6. 设置环境变量 (这些是默认值，Docker 运行时可以用 -e 覆盖)
ENV MYSQL_HOST=10.212.7.238 \
    MYSQL_PORT=3306 \
    MYSQL_USERNAME=root \
    MYSQL_PASSWORD=root \
    REDIS_HOST=192.168.155.129 \
    REDIS_PORT=6379 \
    REDIS_PASSWORD=123456

# 7. 暴露端口
EXPOSE 8081

# 8. 启动命令
ENTRYPOINT ["java", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-Dspring.profiles.active=prod", \
            "-jar", \
            "app.jar"]