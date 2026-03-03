FROM gradle:9.0.0-jdk21 AS builder

WORKDIR /workspace
COPY shiori-java/ /workspace/shiori-java/
WORKDIR /workspace/shiori-java

RUN gradle --no-daemon :shiori-user-service:bootJar -x test

FROM gradle:9.0.0-jdk21

WORKDIR /app
COPY --from=builder /workspace/shiori-java/shiori-user-service/build/libs/*.jar /app/app.jar

ENV NACOS_ADDR=nacos:8848
ENV NACOS_USERNAME=nacos
ENV NACOS_PASSWORD=nacos
ENV SPRING_DATA_REDIS_HOST=redis
ENV SPRING_DATA_REDIS_PORT=6379

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
