# Build stage. The pom is copied first so the dependency layer is cached between builds.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# AOT cache stage: start-up time is what we bleed during a deploy (the new instance isn't serving yet), so record
# what start-up loads and links once here, and let production start from that (JEP 483/514, Java 25).
# The jar is extracted first: the AOT cache only covers classes loaded from plain jars on the class path.
# The training run starts Spring and stops once the context is refreshed (spring.context.exit=onRefresh); it
# never touches a database (schema init off, Hikari connects lazily). If the cache doesn't match at run time,
# the JVM ignores it and starts normally.
FROM eclipse-temurin:25-jre AS aot
WORKDIR /app
COPY --from=build /build/target/cfp.jar cfp.jar
RUN java -Djarmode=tools -jar cfp.jar extract --destination extracted
WORKDIR /app/extracted
RUN java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh -Dspring.sql.init.mode=never -jar cfp.jar

# Runtime stage
FROM eclipse-temurin:25-jre
# Same path as in the aot stage: the cache is only valid for the class path it was recorded with.
WORKDIR /app/extracted
COPY --from=aot /app/extracted .
# Scaleway's database needs TLS; the Compose Postgres doesn't, so application.properties defaults to off.
ENV PGSSLMODE=require
# GET /health reports this as its version: docker build --build-arg GIT_SHA=$(git rev-parse HEAD) .
ARG GIT_SHA=unknown
ENV GIT_SHA=$GIT_SHA
EXPOSE 8080
ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-jar", "cfp.jar"]
