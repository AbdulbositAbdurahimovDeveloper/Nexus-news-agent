// Nexus (news-agent) — PROD deploy pipeline.
// Compose ISHLATILMAYDI (Jenkins'da compose plugin yo'q) — oddiy `docker build` + `docker run`.
// Konteyner `app-network`ga ulanib, o'sha tarmoqdagi mavjud `postgres`ga (DB_HOST=postgres) yetadi.
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    environment {
        // Bizda bitta muhit (PROD) — DEV/PROD tanlovi yo'q
        IMAGE_NAME     = "news-agent"
        CONTAINER_NAME = "news-agent"
        NETWORK_NAME   = "app-network"
        CONTAINER_PORT = "8080"

        IMAGE_TAG      = "prod-${BUILD_NUMBER}"
        FULL_IMAGE     = "${IMAGE_NAME}:prod-${BUILD_NUMBER}"
        LATEST_IMAGE   = "${IMAGE_NAME}:prod-latest"

        // Prod .env — git'da yo'q. Jenkins "Secret file" credential.
        ENV_CREDENTIAL_ID = "nexus-prod-env"

        LOG_DIR      = "/opt/server/logs/projects/nexus/news-agent"
        PROJECT_DIR  = "/opt/server/projects/nexus"
    }

    stages {
        stage('1. Checkout') {
            steps {
                cleanWs()
                checkout scm
            }
        }

        stage('2. Security Gates') {
            steps {
                echo "Secret scan, dependency scan va SBOM (bloklamaydi — exit-code=0)..."
                sh """
                    # Maxfiy kalitlar (secrets) sizishini tekshirish
                    docker run --rm -v "\$(pwd)":/path zricethezav/gitleaks:latest detect --source=/path --no-git --redact --exit-code=0

                    # Kod zaifliklari va noto'g'ri sozlamalar
                    docker run --rm -v "\$(pwd)":/path aquasec/trivy:latest fs --exit-code=0 --severity HIGH,CRITICAL --scanners vuln,secret,misconfig /path

                    # SBOM hisoboti
                    docker run --rm -v "\$(pwd)":/path aquasec/trivy:latest fs --format cyclonedx --output /path/sbom.cdx.json /path
                """
            }
        }

        stage('3. Build Docker Image') {
            steps {
                echo "Docker image yig'ilmoqda (testlar shu bosqichда, JDK 25 ichida ishlaydi)..."
                sh """
                    docker build \
                      -t ${FULL_IMAGE} \
                      -t ${LATEST_IMAGE} \
                      .
                """
            }
        }

        stage('4. Scan Docker Image') {
            steps {
                echo "Docker image zaifliklarga tekshirilmoqda (bloklamaydi)..."
                sh """
                    docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:latest image --exit-code=0 --severity HIGH,CRITICAL ${FULL_IMAGE}
                """
            }
        }

        stage('5. Deploy') {
            steps {
                echo "PROD muhitga xavfsiz deploy qilinmoqda..."

                // Jenkins'dagi maxfiy .env faylini o'qib olamiz
                withCredentials([file(credentialsId: env.ENV_CREDENTIAL_ID, variable: 'SECRET_ENV_FILE')]) {
                    sh """
                        # Papkalarni yaratish
                        mkdir -p ${LOG_DIR}
                        mkdir -p ${PROJECT_DIR}

                        # Maxfiy .env'ni vaqtincha ko'chirish (--env-file uchun)
                        cp "${SECRET_ENV_FILE}" ${PROJECT_DIR}/.env
                        chmod 600 ${PROJECT_DIR}/.env

                        # Tarmoq mavjudligini tekshirish (biz yaratmaymiz, faqat ulanamiz)
                        docker network inspect ${NETWORK_NAME} >/dev/null 2>&1 || {
                          echo "ERROR: ${NETWORK_NAME} tarmog'i topilmadi"
                          exit 1
                        }

                        # Eski konteynerni xavfsiz o'chirish
                        docker rm -f ${CONTAINER_NAME} || true

                        # Yangi konteynerni ishga tushirish.
                        # Host port ochilmaydi — bot outbound (Telegram long polling), monitoring
                        # esa app-network ichida konteyner nomi orqali (news-agent:8080) yetadi.
                        docker run -d \
                          --name ${CONTAINER_NAME} \
                          --restart unless-stopped \
                          --network ${NETWORK_NAME} \
                          --env-file ${PROJECT_DIR}/.env \
                          --read-only \
                          --tmpfs /tmp:rw,noexec,nosuid,size=128m \
                          --cap-drop=ALL \
                          --security-opt no-new-privileges:true \
                          --memory=768m \
                          --cpus=1.0 \
                          --pids-limit=256 \
                          -v ${LOG_DIR}:/app/logs \
                          ${LATEST_IMAGE}

                        # Maxfiy faylni darhol o'chirish
                        rm -f ${PROJECT_DIR}/.env

                        # app-network ichida postgres ko'rinayotganini tekshirish
                        docker exec ${CONTAINER_NAME} getent hosts postgres
                    """
                }
                echo "Muvaffaqiyatli deploy qilindi (PROD)."
            }
        }
    }

    post {
        success {
            echo "✅ Pipeline muvaffaqiyatli yakunlandi."
        }
        failure {
            echo "❌ Pipeline xatolik bilan tugadi. Loglarni tekshiring."
        }
    }
}
