FROM eclipse-temurin:21-jre

WORKDIR /app
COPY shiori-java/shiori-search-indexer-service/build/libs/*.jar /app/app.jar

ENV NACOS_ADDR=nacos:8848
ENV NACOS_USERNAME=nacos
ENV NACOS_PASSWORD=nacos

EXPOSE 8086
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
