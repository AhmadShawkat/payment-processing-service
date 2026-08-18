FROM eclipse-temurin:11-jdk-jammy AS build

WORKDIR /workspace

COPY . .

RUN chmod +x gradlew \
    && ./gradlew bootWar --no-daemon

FROM eclipse-temurin:11-jre-jammy

WORKDIR /app

RUN groupadd --system grails \
    && useradd --system --gid grails --home-dir /app grails \
    && chown grails:grails /app

COPY --from=build --chown=grails:grails /workspace/build/libs/*.war /app/payment-processing-service.war

USER grails

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx512m -Dfile.encoding=UTF-8"

# The development Grails environment uses create-drop H2 and makes this image
# self-contained for the technical-task demo. Data is intentionally ephemeral.
ENTRYPOINT ["java", "-Dgrails.env=development", "-jar", "/app/payment-processing-service.war"]
