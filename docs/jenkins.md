# Jenkins deploy agent

Bu pipeline prod hostdagi Docker socket orqali deploy qiladi. Shuning uchun Jenkins
konteynerida quyidagilar bo'lishi kerak:

- `docker`
- `docker compose` v2 plugin
- hostdagi `/var/run/docker.sock` mount qilingan bo'lishi

## Jenkins image

Repo ichidagi image Docker CLI va Compose v2 plugin bilan Jenkins tayyorlaydi:

```bash
docker build -t nexus-jenkins -f ci/jenkins/Dockerfile .
```

Jenkins konteynerini host Docker socket bilan ishga tushiring:

```bash
docker run -d \
  --name jenkins \
  --restart unless-stopped \
  --group-add "$(stat -c '%g' /var/run/docker.sock)" \
  -p 8081:8080 \
  -p 50000:50000 \
  -v jenkins_home:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  nexus-jenkins
```

Tekshirish:

```bash
docker exec jenkins docker --version
docker exec jenkins docker compose version
```

## Prod sozlamalari

Jenkins Credentials bo'limida `Secret file` yarating:

- ID: `nexus-prod-env`
- file content: prod `.env`

Prod `.env` ichida kamida quyidagilar bo'lishi kerak:

```dotenv
DB_MODE=postgres
DB_HOST=postgres
EXTERNAL_NETWORK=app-network
```

Hostda external network mavjudligini tekshiring:

```bash
docker network inspect app-network
```

Agar yo'q bo'lsa, Postgres konteyneringiz ishlatayotgan network nomini
`EXTERNAL_NETWORK` ga yozing yoki networkni yarating.
