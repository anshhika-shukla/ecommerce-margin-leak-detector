FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY . .
RUN find . -name "*.class" -delete && javac WebServer.java
EXPOSE 10000
CMD ["java", "WebServer"]
