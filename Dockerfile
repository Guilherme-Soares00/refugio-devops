FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src/main src/main
RUN --mount=type=cache,id=refugio-maven,target=/root/.m2,sharing=locked \
    chmod +x mvnw && ./mvnw -B -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 refugio \
    && useradd --uid 10001 --gid refugio --no-create-home --shell /usr/sbin/nologin refugio
ENV SERVER_PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0 -XX:+ExitOnOutOfMemoryError"
COPY --from=build --chown=10001:10001 /build/target/refugio-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
