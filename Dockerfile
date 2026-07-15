FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x ./gradlew

COPY src ./src
RUN ./gradlew bootJar playwrightInstall --no-daemon

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

ENV PLAYWRIGHT_BROWSERS_PATH=/home/app/.cache/ms-playwright

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        fonts-liberation \
        libasound2 \
        libatk-bridge2.0-0 \
        libatk1.0-0 \
        libatspi2.0-0 \
        libcups2 \
        libdbus-1-3 \
        libdrm2 \
        libgbm1 \
        libgtk-3-0 \
        libnspr4 \
        libnss3 \
        libwayland-client0 \
        libx11-6 \
        libx11-xcb1 \
        libxcb1 \
        libxcomposite1 \
        libxdamage1 \
        libxext6 \
        libxfixes3 \
        libxkbcommon0 \
        libxrandr2 \
        libxshmfence1 \
    && rm -rf /var/lib/apt/lists/*

RUN addgroup --system app && adduser --system --ingroup app app

COPY --from=build /workspace/build/libs/*.jar app.jar
COPY --from=build --chown=app:app /root/.cache/ms-playwright /home/app/.cache/ms-playwright

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
