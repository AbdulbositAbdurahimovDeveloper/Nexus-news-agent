# 1-bosqich: Loyihani yig'ish (Build stage) — JDK 25 + Maven wrapper
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Avval bog'liqliklarni cache'laymiz
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline

# So'ng kodni yig'amiz (testlar Jenkins'ning alohida "Test" bosqichida ishlaydi)
COPY src ./src
RUN ./mvnw -q clean package -DskipTests

# 2-bosqich: Ishga tushirish (Runtime stage) — JRE 25
FROM eclipse-temurin:25-jre
WORKDIR /app

ENV TZ=Asia/Tashkent

# curl (healthcheck uchun), vaqt zonasini Toshkentga sozlash va root bo'lmagan 'app' foydalanuvchi
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app/logs \
    && chown -R app:app /app \
    && chmod 750 /app/logs

# Jar faylini ko'chirib olish va egasini o'zgartirish
COPY --from=build /app/target/*.jar app.jar
RUN chown app:app app.jar

# Root bo'lmagan foydalanuvchiga o'tish
USER app

EXPOSE 8080

# Sog'lomlikni tekshirish — Spring Boot Actuator (application.yaml'da health ochilgan)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-Duser.timezone=Asia/Tashkent", "-jar", "app.jar"]
