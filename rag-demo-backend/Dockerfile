FROM alibabadragonwell/dragonwell:25.0.3.0.3.9-standard-ga-anolis

ARG JAR_FILE

WORKDIR /app

COPY ${JAR_FILE} /app/app.jar

RUN mkdir -p /app/uploads /app/log

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
