# ===== Build bosqichi (JDK 25) =====
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline

COPY src src
RUN ./mvnw -q package -DskipTests

# ===== Runtime bosqichi (JRE 25) =====
FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# H2 file rejimida ma'lumotlar shu yerda saqlanadi (volume)
VOLUME /data
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
