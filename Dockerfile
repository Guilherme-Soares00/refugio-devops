ARG JAVA_REF=c76af11aef2d871a130b4c3db1c22bd71dfe60dd

FROM eclipse-temurin:25-jdk-jammy AS build
ARG JAVA_REF
WORKDIR /build
ADD https://github.com/Guilherme-Soares00/refugio.git#${JAVA_REF} /build/
RUN --mount=type=cache,id=refugio-maven,target=/root/.m2,sharing=locked \
    chmod +x mvnw && ./mvnw -B -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre-jammy
ARG JAVA_REF
LABEL org.opencontainers.image.source="https://github.com/Guilherme-Soares00/refugio" \
    org.opencontainers.image.revision="${JAVA_REF}"
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
