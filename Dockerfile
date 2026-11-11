FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q
COPY src/ src/
RUN ./mvnw package -DskipTests -q

FROM eclipse-temurin:25-jre-noble
WORKDIR /app

RUN groupadd --gid 10001 cobranca \
    && useradd --uid 10001 --gid cobranca --no-create-home --no-log-init cobranca

COPY --from=build --chown=cobranca:cobranca /app/target/*.jar app.jar

USER cobranca

EXPOSE 8082

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
