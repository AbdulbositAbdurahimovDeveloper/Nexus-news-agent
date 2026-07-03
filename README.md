# news-agent

Interaktiv Telegram yangiliklar agenti — tanlagan kategoriyalaringiz bo'yicha
yangiliklarni AI (Anthropic/OpenAI/Gemini) xulosasi bilan belgilangan vaqtda yuboradi.

**Stack:** Java 25 · Spring Boot 4.1 (Spring Framework 7) · Liquibase · H2/PostgreSQL · Docker

## Ishga tushirish

```bash
cp .env.example .env
# .env ga TELEGRAM_BOT_TOKEN va LLM API kalitini kiriting
docker compose up -d --build
docker compose logs -f app
```

Default rejim — **H2 file** (hech qanday baza o'rnatish shart emas, data `./data/` da saqlanadi).

## DB rejimlari

| Rejim | .env | Buyruq |
|---|---|---|
| H2 file (default) | `DB_MODE=h2` | `docker compose up -d --build` |
| Postgres bundled | `DB_MODE=postgres`, `DB_HOST=db` | `docker compose --profile postgres up -d --build` |
| Postgres boshqa konteynerda | `DB_HOST=<konteyner>`, `EXTERNAL_NETWORK=<tarmoq>` | `docker compose -f docker-compose.yml -f docker-compose.external-db.yml up -d --build` |
| Postgres host kompyuterda | `DB_HOST=host.docker.internal` | `docker compose up -d --build` |

## Bot

`/start` → inline menyu: kategoriya (6 ta) → vaqtlar (07:00–22:00 grid) → davriylik
(har kuni / kun ora / har 3 kun / haftada 1). `/list` — obunalarni boshqarish.

## Lokal ishlab chiqish (Docker'siz)

```bash
./mvnw spring-boot:run    # H2 file rejimida, data ./data/ da
./mvnw test
```

To'liq texnik reja: [docs/task.md](docs/task.md)

## Jenkins deploy

Prod deploy pipeline Docker CLI va Compose v2 o'rnatilgan Jenkins agentni talab qiladi.
Jenkins konteynerini tayyorlash: [docs/jenkins.md](docs/jenkins.md)
