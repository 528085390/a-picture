FROM eclipse-temurin:11-jre-jammy
WORKDIR /app
RUN useradd --system --uid 10001 --create-home appuser
COPY app.jar /app/app.jar
COPY application-prod.yml /app/config/application-prod.yml
RUN chown -R appuser:appuser /app
USER appuser
EXPOSE 8000
ENV TZ=Asia/Shanghai JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Shanghai -Xms256m -Xmx2g"
ENTRYPOINT ["java","-jar","/app/app.jar","--spring.config.additional-location=file:/app/config/","--spring.profiles.active=prod"]
