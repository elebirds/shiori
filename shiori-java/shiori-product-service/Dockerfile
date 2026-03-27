FROM eclipse-temurin:21-jre

WORKDIR /app
COPY shiori-java/shiori-product-service/build/libs/*.jar /app/app.jar

ENV NACOS_ADDR=nacos:8848
ENV NACOS_USERNAME=nacos
ENV NACOS_PASSWORD=nacos
ENV SPRING_DATA_REDIS_HOST=redis
ENV SPRING_DATA_REDIS_PORT=6379

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
