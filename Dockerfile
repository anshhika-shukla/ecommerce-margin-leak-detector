FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY . .

RUN find . -name "*.class" -delete && javac -d . $(find . -type f -name "*.java" -not -path "./.git/*")

EXPOSE 10000

CMD ["java", "WebServer"]