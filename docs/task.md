# news-agent — Ish rejasi va texnik topshiriq

> **Loyiha:** Nexus / news-agent — interaktiv Telegram yangiliklar agenti
> **Stack:** Java 25 (MS OpenJDK) · Spring Boot 4.1 (Spring Framework 7) · Maven · Liquibase · H2 (default) / PostgreSQL · Docker
> **Holat:** Reja tasdiqlangach bosqichma-bosqich bajariladi. Har bosqich tugagach `[x]` belgilanadi.

---

## 0. Umumiy tavsif

Foydalanuvchi Telegram botga kirib obuna sozlaydi: kategoriya → vaqt(lar) → davriylik
(masalan: *IT — har kuni 9:00 va 12:00*, *O'zbekiston — kun ora 13:00*).
Agent belgilangan vaqtda o'sha kategoriya RSS manbalaridan yangi maqolalarni oladi,
tanlangan LLM (Anthropic / OpenAI / Gemini) bilan xulosa qiladi va chatga chiroyli
Markdown formatda yuboradi. Yuborilganlar bazaga yoziladi — takror yuborilmaydi.

### Arxitektura oqimi

```
@Scheduled poller (har daqiqa)
   └─> subscriptions jadvalidan "vaqti kelgan" obunalarni topadi
        └─> RSS fetcher (kategoriya feed'lari) ─> dedup (sent_articles)
             └─> LlmClient (provider .env dan) ─> xulosa
                  └─> TelegramClient (HTML parse mode) ─> chatga yuboradi
                       └─> sent_articles + subscriptions.last_sent_at yangilanadi
```

### 6 ta statik kategoriya

| Kod | Nomi | Namuna RSS manbalar |
|---|---|---|
| `IT` | 💻 IT/Texnologiya | Hacker News, TechCrunch, The Verge |
| `WORLD` | 🌍 Dunyo | BBC, Reuters, Al Jazeera |
| `UZ` | 🇺🇿 O'zbekiston | Kun.uz, Gazeta.uz, Daryo.uz |
| `ECONOMY` | 📈 Iqtisod/Biznes | Financial Times, Bloomberg (RSS), Spot.uz |
| `SCIENCE` | 🔬 Fan | Nature News, ScienceDaily |
| `SPORT` | ⚽ Sport | ESPN, BBC Sport, Championat.asia |

Feed ro'yxati `application.yaml` da — kod o'zgartirmasdan almashtirish mumkin.

---

## 1-bosqich: Loyiha skeleti va pom.xml

- [x] 1.1 `pom.xml` ni to'ldirish:
  - parent: `spring-boot-starter-parent:4.1.0` (Spring Boot 4.0+ sharti bajariladi)
  - `java.version=25`
  - starter'lar: `web` (RestClient + health endpoint uchun), `data-jpa`, `validation`
  - `liquibase-core` (Flyway EMAS — mijoz talabi)
  - drayverlar: `h2` (runtime), `postgresql` (runtime) — ikkalasi ham jar ichida bo'ladi,
    qaysi biri ishlashini profil hal qiladi
  - `rome` (RSS parsing uchun standart kutubxona)
  - `lombok`, `spring-boot-starter-test` (test)
- [x] 1.2 Keraksiz qolgan `spring-boot-starter-data-jpa-test` ni olib tashlash
- [x] 1.3 Paket strukturasi:

```
org.platform.nexus
├── NexusApplication.java
├── config/          # AppProperties, LlmProperties, RestClient beanlar
├── category/        # NewsCategory enum (6 ta statik)
├── feed/            # RssFetcher, Article record
├── llm/             # LlmClient interface, AnthropicClient, OpenAiClient, GeminiClient
├── telegram/        # TelegramClient (long polling), UpdateDispatcher,
│                    # handlers/ (komanda va callback handlerlar), Keyboards, Messages
├── subscription/    # Subscription entity, repo, service
├── sent/            # SentArticle entity, repo
└── scheduler/       # DeliveryScheduler (poller), DigestService
```

## 2-bosqich: Baza qatlami — Liquibase + JPA

- [x] 2.1 Liquibase changelog strukturasi (XML, **DB-agnostik** — H2 va Postgres'da
  bir xil ishlashi shart):

```
src/main/resources/db/changelog/
├── db.changelog-master.xml
├── 001-create-subscriptions.xml
└── 002-create-sent-articles.xml
```

- [x] 2.2 `subscriptions` jadvali:

| ustun | tip | izoh |
|---|---|---|
| id | bigint identity PK | |
| chat_id | bigint, not null | Telegram chat |
| category | varchar(20), not null | NewsCategory kodi |
| send_times | varchar(200), not null | vergul bilan: `09:00,12:00` |
| interval_days | int, default 1 | 1=har kuni, 2=kun ora, N=har N kun |
| enabled | boolean, default true | |
| last_sent_date | date, nullable | interval hisobi uchun |
| created_at | timestamp | |
| unique(chat_id, category) | | bitta chat–kategoriya = bitta obuna |

- [x] 2.3 `sent_articles` jadvali:

| ustun | tip | izoh |
|---|---|---|
| id | bigint identity PK | |
| chat_id | bigint, not null | |
| category | varchar(20), not null | |
| url_hash | varchar(64), not null | maqola URL SHA-256 |
| title | varchar(500) | |
| sent_at | timestamp | |
| unique(chat_id, url_hash) | | dedup kaliti |

- [x] 2.4 Entity + Spring Data repository'lar; `ddl-auto: validate`
  (sxemani faqat Liquibase boshqaradi — bu production-to'g'ri yondashuv)

## 3-bosqich: DB rejimlari — H2 (default) va PostgreSQL

> **Talab:** loyihani kimga berilsa ham `docker compose up` bilan darhol ishlashi kerak.
> Default — H2 **file rejimida** (RAM emas!). Postgres tanlansa — 3 xil ulanish yo'li.

- [x] 3.1 Spring profillari:
  - `h2` (default profil): `jdbc:h2:file:/data/newsagent;AUTO_SERVER=TRUE` —
    ma'lumot faylda saqlanadi, konteyner o'chsa ham volume'da qoladi
  - `postgres`: `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}`
- [x] 3.2 Rejim tanlash faqat `.env` orqali, kod o'zgarmaydi:

| Rejim | .env sozlama | Nima bo'ladi |
|---|---|---|
| **1. H2 file (DEFAULT)** | `DB_MODE=h2` (yoki umuman yozilmasa) | Hech narsa o'rnatish shart emas, data `./data/` volume'da |
| **2a. Postgres — bundled (postgres tanlanganda default)** | `DB_MODE=postgres`, `DB_HOST=db` | Compose o'zi `postgres:17` konteyner ko'taradi, app kutib ulanadi |
| **2b. Postgres — boshqa docker konteyner** | `DB_MODE=postgres`, `DB_HOST=<konteyner-nomi>`, `EXTERNAL_NETWORK=<tarmoq>` | Mavjud postgres konteyner tarmog'iga ulanadi (docker network orqali) |
| **2c. Postgres — host kompyuterda** | `DB_MODE=postgres`, `DB_HOST=host.docker.internal` | Kompyuterda o'rnatilgan postgres'ga ulanadi (`extra_hosts: host-gateway`) |

- [x] 3.3 Liquibase changeset'lar ikkala DB'da test qilinadi (H2 PostgreSQL
  compatibility mode'da ishlatiladi: `MODE=PostgreSQL`)

## 4-bosqich: RSS fetcher

- [x] 4.1 `RssFetcher` — Rome kutubxonasi bilan, har kategoriya uchun feed ro'yxati
  `application.yaml` dan (`news.categories.IT.feeds[0]=...`)
- [x] 4.2 `Article` record: title, url, source, publishedAt, description
- [x] 4.3 Xatoga chidamlilik: bitta feed yiqilsa qolganlari ishlashda davom etadi
  (log + skip), timeout 10s
- [x] 4.4 Oxirgi 24 soat ichidagi maqolalargina olinadi (eski arxivni qayta yubormaslik)

## 5-bosqich: LLM qatlami

- [x] 5.1 `LlmClient` interface: `String summarize(List<Article> articles, NewsCategory category)`
- [x] 5.2 3 ta implementatsiya, har biri `@ConditionalOnProperty(name="llm.provider", havingValue=...)`:
  - `AnthropicClient` — Messages API, model `claude-sonnet-4-6` (arzon/tez default)
  - `OpenAiClient` — Chat Completions
  - `GeminiClient` — generateContent
- [x] 5.3 Hammasi Spring `RestClient` bilan (Spring Boot 4 tavsiyasi, RestTemplate o'rniga)
- [x] 5.4 Prompt: o'zbekcha qisqa digest, har maqola: qalin sarlavha + 1-2 gap xulosa
  + manba havolasi. Chiqish Telegram MarkdownV2 ga mos bo'lishi kerak
- [x] 5.5 Xato bo'lsa (API kalit noto'g'ri, quota): log + foydalanuvchiga xushmuomala
  xabar, scheduler yiqilmaydi

## 6-bosqich: Telegram bot — komandalar + inline tugmalar

> **Talab:** faqat komanda emas — chiroyli UI: inline tugmalar bilan kategoriya/vaqt/
> davriylik tanlash, hamma xabar Markdown bilan bezatilgan.

- [x] 6.1 `TelegramClient` — long polling (`getUpdates`, offset bilan), alohida
  virtual thread'da ishlaydi; `sendMessage`, `editMessageText`, `answerCallbackQuery`
- [x] 6.2 Komandalar:
  - `/start` — salomlashish + asosiy menyu (inline tugmalar)
  - `/menu` — asosiy menyu qayta chiqarish
  - `/list` — obunalarim (har birida ✏️ tahrirlash / 🗑 o'chirish tugmasi)
  - `/help` — qo'llanma
- [x] 6.3 Inline UI oqimi (callback_query bilan, xabar edit qilinadi — chat toza qoladi):

```
Asosiy menyu
├── ➕ Obuna qo'shish
│    └── Kategoriya tanlash (6 tugma, 2 ustun)
│         └── Vaqt tanlash (07:00–22:00 grid, bir nechta tanlash mumkin, ✅ belgi)
│              └── Davriylik: [Har kuni] [Kun ora] [Har 3 kun] [Haftada 1]
│                   └── 📋 Tasdiqlash xabari + [Saqlash] [Bekor]
├── 📋 Obunalarim  →  har biriga [✏️ O'zgartirish] [🗑 O'chirish] [⏸ Pauza]
└── ℹ️ Yordam
```

- [x] 6.4 Callback state: `callback_data` ichida kodlangan (`sub:cat:IT`,
  `sub:time:09:00`, `sub:freq:2`) — serverda session saqlash shart emas,
  yarim tugallangan tanlov xabarning o'zida yashaydi
- [x] 6.5 Digest xabar formati (MarkdownV2):

```
📰 *IT yangiliklari* — 02.07.2026 09:00

1️⃣ *Sarlavha bold ko'rinishda*
   Qisqa xulosa 1-2 gap...
   🔗 [Manba: TechCrunch](url)

2️⃣ ...
```

- [x] 6.6 MarkdownV2 escape utility (Telegram maxsus belgilarni talab qiladi:
  `_ * [ ] ( ) ~ \` > # + - = | { } . !`)

## 7-bosqich: Scheduler

- [x] 7.1 `DeliveryScheduler` — `@Scheduled(cron = "0 * * * * *")` har daqiqa:
  hozirgi `HH:mm` ga mos, `enabled=true`, interval sharti bajarilgan
  (`last_sent_date + interval_days <= bugun`) obunalarni topadi
- [x] 7.2 Har obuna uchun `DigestService.deliver(subscription)` — fetch → dedup →
  LLM → send → yozish; har biri alohida try/catch (biri yiqilsa boshqasiga ta'sir yo'q)
- [x] 7.3 Vaqt zonasi: `Asia/Tashkent` (konfiguratsiyada, `TZ` env bilan o'zgartiriladi)
- [x] 7.4 Virtual threads: `spring.threads.virtual.enabled=true` — LLM/RSS kutishlari
  arzon (Java 21+ / Spring Boot 3.2+ imkoniyati, Java 25 da barqaror)

## 8-bosqich: Docker

- [x] 8.1 `Dockerfile` — multi-stage, **ikkala bosqich ham JDK/JRE 25**:
  - build: `maven:3.9-eclipse-temurin-25` (yo'q bo'lsa: `eclipse-temurin:25` + mvnw)
  - runtime: `eclipse-temurin:25-jre`
  - layered jar (`spring-boot:build-image` emas, oddiy COPY --from, tez build)
- [x] 8.2 `docker-compose.yml`:
  - `app` servisi: `.env` dan o'qiydi, `./data:/data` volume (H2 fayl uchun),
    restart policy, healthcheck (`/actuator/health` yoki port check)
  - `db` servisi (`postgres:17-alpine`): **compose profile `postgres`** ostida —
    `docker compose up` da ko'tarilmaydi, faqat
    `docker compose --profile postgres up` da ko'tariladi; healthcheck
    (`pg_isready`), volume
  - `extra_hosts: host.docker.internal:host-gateway` (2c rejim uchun, Linux'da shart)
- [x] 8.3 Ishga tushirish stsenariylari hujjatda (README bo'limi):

```bash
# 1. Default — H2 file, hech narsa sozlash shart emas:
docker compose up -d --build

# 2a. Bundled postgres bilan:
#    .env: DB_MODE=postgres  DB_HOST=db
docker compose --profile postgres up -d --build

# 2b. Mavjud postgres konteyner bilan:
#    .env: DB_MODE=postgres  DB_HOST=<konteyner>  EXTERNAL_NETWORK=<tarmoq>
docker compose -f docker-compose.yml -f docker-compose.external-db.yml up -d --build

# 2c. Host kompyuterdagi postgres bilan:
#    .env: DB_MODE=postgres  DB_HOST=host.docker.internal
docker compose up -d --build
```

## 9-bosqich: Konfiguratsiya va build

- [x] 9.1 `.env.example` (hamma o'zgaruvchi izohli) va undan `.env` nusxa —
  **API kalitlar bo'sh qoladi, foydalanuvchi o'zi to'ldiradi**:

```env
# --- Telegram ---
TELEGRAM_BOT_TOKEN=          # @BotFather dan olinadi

# --- LLM ---
LLM_PROVIDER=anthropic       # anthropic | openai | gemini
ANTHROPIC_API_KEY=
OPENAI_API_KEY=
GEMINI_API_KEY=

# --- Database ---
DB_MODE=h2                   # h2 (default, file) | postgres
DB_HOST=db                   # postgres rejimida: db | <konteyner> | host.docker.internal
DB_PORT=5432
DB_NAME=news_agent
DB_USERNAME=postgres
DB_PASSWORD=postgres

# --- App ---
TZ=Asia/Tashkent
```

- [x] 9.2 `.gitignore` ga `.env` va `data/` qo'shish
- [x] 9.3 `mvn clean package -DskipTests` — muvaffaqiyatli o'tishi
- [x] 9.4 Lokal smoke test (H2 rejimda, docker'siz `java -jar`)

> **Implementatsiya eslatmasi:** xabar formati MarkdownV2 emas, **HTML parse mode** bilan
> qilindi — vizual natija bir xil (bold, havolalar), lekin LLM chiqishini ekranlash
> xavfsizroq: MarkdownV2'da 18 ta maxsus belgi ekranlanishi kerak, HTML'da faqat 3 ta
> (`& < >`). Yana bir farq: Anthropic uchun rasmiy `com.anthropic:anthropic-java` SDK
> ishlatildi (Anthropic tavsiyasi), OpenAI/Gemini — RestClient bilan.

## 10-bosqich: Ishga tushirish va verifikatsiya

> ⏸ **PAUZA:** foydalanuvchi `.env` ga TELEGRAM_BOT_TOKEN va LLM API kalitini kiritadi.

- [x] 10.1 `docker compose up -d --build` (default H2 rejim)
- [x] 10.2 `docker compose logs -f app` — tahlil:
  - [x] Liquibase migratsiyalar o'tdi (`subscriptions`, `sent_articles` yaratildi)
  - [x] `LLM_PROVIDER` o'qildi, to'g'ri client bean yaratildi (startup log'da chiqaramiz)
  - [x] Telegram long polling boshlandi (bot username log'da)
  - [x] Scheduler birinchi tick xatosiz
- [ ] 10.3 Postgres rejimini ham tekshirish: `--profile postgres` bilan qayta ko'tarish,
  Liquibase postgres'da ham o'tganini tasdiqlash
- [ ] 10.4 End-to-end test: botda `/start` → obuna yaratish (inline tugmalar) →
  test uchun yaqin vaqt qo'yish → digest kelishini kutish → `sent_articles` da yozuv
- [ ] 10.5 Yakuniy hisobot + Spring Boot 4 / Java 25 farqlari xulosasi

---

## Spring Boot 4.x da e'tibor beriladigan farqlar (3.x dan kelganlar uchun)

Bajarish davomida har biri joyida alohida izohlanadi:

1. **Test starter bo'linishi** — `spring-boot-starter-test` endi modulli;
   sliced testlar uchun alohida starter'lar bor
2. **`@MockBean` olib tashlandi** → `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`)
3. **Autoconfigure paketlar ko'chgan** — masalan `org.springframework.boot.jdbc.autoconfigure.*`
   (buni 1-sessiyada stack trace'da ko'rdik)
4. **RestClient / HTTP interface client'lar** — RestTemplate o'rniga standart yo'l
5. **Jakarta EE 11 baseline**, Hibernate 7
6. **Java 25** — virtual threads barqaror, Compact Object Headers (`-XX:+UseCompactObjectHeaders`)
   xotira tejaydi; Lombok/Mockito'ning `sun.misc.Unsafe` warning'lari — zararsiz, kutubxona yangilanishi bilan ketadi

---

## Bajarish tartibi xulosasi

| # | Bosqich | Natija | Tasdiq nuqtasi |
|---|---|---|---|
| 1 | pom.xml + skelet | kompilyatsiya o'tadi | — |
| 2 | Liquibase + entity | migratsiya H2'da o'tadi | — |
| 3 | DB rejimlari | profillar ishlaydi | — |
| 4-5 | RSS + LLM | unit darajada tayyor | — |
| 6 | Telegram bot UI | komandalar + inline oqim | — |
| 7 | Scheduler | poller ishlaydi | — |
| 8 | Docker | image build bo'ladi | — |
| 9 | .env + build | `mvn package` ✅ | ⏸ kalitlarni kutish |
| 10 | Run + verifikatsiya | to'liq ishlaydi | ✅ yakuniy tasdiq |
