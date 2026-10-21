FROM eclipse-temurin:17-jre
WORKDIR /app
COPY backend.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
