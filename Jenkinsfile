// Nexus (news-agent) — PROD deploy pipeline.
// App tashqi `app-network` orqali o'sha tarmoqdagi mavjud `postgres` konteyneriga ulanadi.
// Jenkins prod hostning docker socket'idan foydalanadi (docker + docker compose kerak).
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    environment {
        // Compose fayllar va project nomini env orqali beramiz (flag emas) —
        // shunda `docker compose` ning barcha versiyalarida ishlaydi ('-p' flag muammosi bo'lmaydi).
        // COMPOSE_FILE: asosiy + tashqi DB overlay (app-network'ga qo'shadi), Linux'da ':' bilan ajratiladi.
        COMPOSE_FILE         = 'docker-compose.yml:docker-compose.external-db.yml'
        COMPOSE_PROJECT_NAME = 'nexus-prod'
        APP_CONTAINER  = 'news-agent'
        EXTERNAL_NET   = 'app-network'
        // Prod .env — git'da yo'q. Jenkins'da "Secret file" credential sifatida saqlanadi.
        // Manage Jenkins → Credentials → Secret file, ID = nexus-prod-env
        ENV_CRED_ID    = 'nexus-prod-env'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Prod .env tayyorlash') {
            steps {
                // .env.prod git'ga kirmaydi — credential'dan .env qilib yozamiz.
                // Bu fayl ham compose substitution (${EXTERNAL_NETWORK} ...), ham
                // servicening `env_file: .env` uchun ishlatiladi.
                withCredentials([file(credentialsId: env.ENV_CRED_ID, variable: 'ENV_FILE')]) {
                    sh 'cp "$ENV_FILE" .env'
                }
                // Prod .env quyidagilarga ega bo'lishi shart:
                //   DB_MODE=postgres
                //   DB_HOST=postgres          (app-network'dagi mavjud konteyner nomi)
                //   EXTERNAL_NETWORK=app-network
                sh '''
                    grep -q '^EXTERNAL_NETWORK=app-network' .env || { echo "❌ .env: EXTERNAL_NETWORK=app-network bo'lishi kerak"; exit 1; }
                    grep -q '^DB_HOST=postgres'            .env || echo "⚠️  .env: DB_HOST=postgres emas — app-network'dagi postgres konteyner nomini tekshiring"
                '''
            }
        }

        stage('Tarmoqni tekshirish') {
            steps {
                // Tashqi tarmoq mavjudligini tasdiqlaymiz (biz yaratmaymiz, faqat ulanamiz).
                sh 'docker network inspect "$EXTERNAL_NET" >/dev/null 2>&1 || { echo "❌ $EXTERNAL_NET topilmadi"; exit 1; }'
            }
        }

        stage('Compose aniqlash') {
            steps {
                // Jenkins konteynerida `docker compose` (v2) yoki `docker-compose` (v1) borligini aniqlaymiz.
                script {
                    if (sh(script: 'docker compose version', returnStatus: true) == 0) {
                        env.DC = 'docker compose'
                    } else if (sh(script: 'docker-compose version', returnStatus: true) == 0) {
                        env.DC = 'docker-compose'
                    } else {
                        error("Na 'docker compose' (v2), na 'docker-compose' (v1) topildi. Jenkins konteynerига compose o'rnating.")
                    }
                    echo "Compose komandasi: ${env.DC}"
                }
            }
        }

        stage('Build') {
            steps {
                // COMPOSE_FILE / COMPOSE_PROJECT_NAME env'dan olinadi
                sh "${DC} build --pull"
            }
        }

        stage('Deploy') {
            steps {
                // Bundled `db` KO'TARILMAYDI (--profile postgres yo'q) — prod'da mavjud postgres ishlatiladi.
                sh "${DC} up -d --remove-orphans"
            }
        }

        stage('Healthcheck') {
            steps {
                sh """
                    echo "Konteyner sog'lig'ini kutyapmiz..."
                    for i in \$(seq 1 30); do
                        status=\$(docker inspect -f '{{.State.Health.Status}}' ${APP_CONTAINER} 2>/dev/null || echo starting)
                        echo "[\$i] holat: \$status"
                        [ "\$status" = "healthy" ]   && { echo "✅ App sog'lom"; exit 0; }
                        [ "\$status" = "unhealthy" ] && { docker logs --tail 80 ${APP_CONTAINER}; exit 1; }
                        sleep 5
                    done
                    echo "❌ Healthcheck timeout"; docker logs --tail 80 ${APP_CONTAINER}; exit 1
                """
            }
        }
    }

    post {
        success {
            echo "✅ Deploy tayyor — ${APP_CONTAINER} '${EXTERNAL_NET}' orqali postgres'ga ulandi."
        }
        failure {
            echo "❌ Deploy muvaffaqiyatsiz — yuqoridagi loglarni tekshiring."
        }
        always {
            sh 'docker image prune -f || true'
            // Secret workspace'da qolib ketmasin
            sh 'rm -f .env || true'
        }
    }
}
