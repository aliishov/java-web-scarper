# Java Web Scarper layihəsi üzrə developer bələdçisi

Bu sənəd yeni qoşulan developer üçün layihənin ümumi arxitekturasını, paket strukturunu, Git qaydalarını, commit siyasətini və əsas development prinsiplərini izah edir.

Layihə Java/Spring Boot backend tətbiqidir. Məqsəd xəbər saytlarından və sonrakı mərhələdə sosial şəbəkələrdən publik postları toplamaq, normalizasiya etmək və PostgreSQL bazasında saxlamaqdır.

## Texniki stack

- Java 21
- Spring Boot 4.x
- Spring Web
- Spring Data JPA
- Spring Validation
- Spring Actuator
- PostgreSQL
- Flyway
- Playwright Java
- JSoup
- Lombok
- Springdoc OpenAPI / Swagger
- Docker Compose
- Gradle

## Əsas biznes anlayışları

Layihədə əsas domain modellər bunlardır:

- `Source` - xəbər saytı və ya sosial platforma mənbəyi.
- `Author` - post müəllifi. Xəbər saytlarında müəllif adətən saytın özüdür.
- `Post` - toplanmış publikasiya.
- `PostMedia` - postun şəkil/video media faylları.
- `Keyword` - scraping üçün istifadə olunan açar söz.
- `PostKeyword` - post və keyword arasında əlaqə, əlavə məlumat saxlamaq üçün istifadə olunur.
- `ScrapeJob` - scraping run tarixçəsi.

## Əsas paket strukturu

Əsas Java kodu burada yerləşir:

```text
src/main/java/org/raul/javawebscarper
```

Cari struktur:

```text
org.raul.javawebscarper
├── browser
├── config
├── controller
├── dto
├── exception
├── mapper
├── model
│   └── enumerated
├── orchestrator
├── repository
├── scheduler
├── scraper
│   ├── adapter
│   ├── engine
│   ├── registry
│   └── support
├── service
├── tools
└── util
```

## Paketlərin məsuliyyəti

### `browser`

Playwright üzərində ümumi browser abstraction burada saxlanılır.

Burada olmalıdır:

- `BrowserEngine`
- `BrowserSessionFactory`
- `BrowserSession`
- `BrowserPage`
- `BrowserSessionOptions`
- browser konfiqurasiyaları və exception-lar

Qayda:

- Platforma-specific logic burada olmamalıdır.
- TikTok, Facebook, Instagram və ya X üçün xüsusi selector/search logic `browser` paketinə qoyulmur.
- Storage state dəstəyi generic qalmalıdır.

### `config`

Spring configuration və `@ConfigurationProperties` class-ları burada saxlanılır.

Burada olmalıdır:

- scheduling config
- JPA auditing config
- ObjectMapper config
- Swagger config
- scheduler və scraper engine properties

Qayda:

- Yeni YAML property əlavə edilirsə, ona uyğun properties class burada və ya domain-specific adapter paketində olmalıdır.
- Secret dəyərlər `application.yaml` içində hardcoded olmamalıdır.

### `controller`

REST API endpoint-ləri burada saxlanılır.

Burada olmalıdır:

- `SourceController`
- `KeywordController`
- `AuthorController`
- `PostController`
- `ScrapeJobController`

Qayda:

- Controller entity qaytarmamalıdır.
- Bütün response-lar `BaseResponseDTO` wrapper ilə qaytarılmalıdır.
- Controller birbaşa repository istifadə etməməlidir.
- Controller service layer ilə danışmalıdır.

Nümunə response tipi:

```java
ResponseEntity<BaseResponseDTO<SourceResponseDTO>>
ResponseEntity<BaseResponseDTO<PageResponseDTO<PostResponseDTO>>>
ResponseEntity<BaseResponseDTO<Void>>
```

### `dto`

API request/response və scraper transfer modelləri burada saxlanılır.

Struktur:

```text
dto
├── common
├── request
│   ├── author
│   ├── keyword
│   ├── post
│   ├── scrapejob
│   └── source
├── response
│   ├── author
│   ├── keyword
│   ├── post
│   ├── scrapejob
│   └── source
└── scraper
```

Qayda:

- Bütün DTO class adları `DTO` suffix ilə bitməlidir.
- Request DTO-lar `dto.request.<domain>` altında olmalıdır.
- Response DTO-lar `dto.response.<domain>` altında olmalıdır.
- Scraper nəticə modelləri `dto.scraper` altında olmalıdır.
- Entity-lər API response kimi istifadə olunmamalıdır.

### `dto.common`

Ümumi API response modelləri burada saxlanılır.

Əsas class-lar:

- `BaseResponseDTO`
- `PageResponseDTO`

Qayda:

- Public API cavabları vahid formatda olmalıdır.
- Pagination üçün mümkün olduqda `PageResponseDTO` istifadə edilməlidir.

### `exception`

Custom exception-lar və global exception handling burada saxlanılır.

Burada olmalıdır:

- `ResourceNotFoundException`
- `DuplicateResourceException`
- `BadRequestException`
- `GlobalExceptionHandler`

Qayda:

- API error response-ları normalizasiya edilməlidir.
- Stacktrace client-ə qaytarılmamalıdır.
- Unexpected exception-lar log edilməlidir.

### `mapper`

Entity və DTO arasında mapping class-ları burada saxlanılır.

Qayda:

- Dependency olmayan mapper utility/static ola bilər.
- Repository/service/başqa mapper dependency-si lazımdırsa, mapper Spring bean ola bilər.
- Mapper business logic saxlamamalıdır.

### `model`

JPA entity-lər burada saxlanılır.

Əsas entity-lər:

- `BaseEntity`
- `Source`
- `Author`
- `Keyword`
- `Post`
- `PostMedia`
- `PostKeyword`
- `ScrapeJob`

Qayda:

- Entity-lərdə əlaqələr UUID field kimi yox, JPA relation kimi saxlanmalıdır.
- `@ManyToOne(fetch = FetchType.LAZY)` istifadə edilməlidir.
- Collection relation-lar ehtiyac olduqda cascade və orphan removal ilə idarə olunmalıdır.
- Entity-ləri birbaşa JSON response kimi qaytarmaq olmaz.
- `@Data` entity-lərdə istifadə edilməməlidir.

### `model.enumerated`

Enum-lar burada saxlanılır.

Cari enum nümunələri:

- `SourceType`
- `MediaType`
- `ScrapeJobStatus`
- `ScrapeJobRunType`
- `Language`

Qayda:

- DB-də enum dəyərləri `EnumType.STRING` kimi saxlanmalıdır.
- Yeni enum dəyəri əlavə ediləndə migration və backward compatibility yoxlanmalıdır.

### `repository`

Spring Data JPA repository interfeysləri burada saxlanılır.

Qayda:

- Repository yalnız persistence sorğuları üçün istifadə olunmalıdır.
- Business logic repository-də olmamalıdır.
- Controller repository çağırmamalıdır.
- Query metodları aydın və domain-ə uyğun adlandırılmalıdır.

### `service`

Business logic burada saxlanılır.

Burada olmalıdır:

- CRUD service-lər
- domain validation
- deduplication
- ingestion logic
- source-keyword language support

Əsas service-lər:

- `SourceService`
- `KeywordService`
- `AuthorService`
- `PostService`
- `ScrapeJobService`
- `ScrapedPostIngestionService`
- `SourceLanguageSupportService`

Qayda:

- Controller-lər service çağırmalıdır.
- Repository access service içində qalmalıdır.
- Transaction boundary service səviyyəsində idarə olunmalıdır.
- Scraper adapter DB-yə birbaşa yazmamalıdır.

### `scheduler`

Cron və scheduled job trigger-ləri burada saxlanılır.

Əsas class-lar:

- `ScrapeJobScheduler`
- `DailyPreviousDayScrapingScheduler`
- `PreviousDayDateRangeResolver`
- `ScrapeDateRange`

Qayda:

- Scheduler özü business logic saxlamamalıdır.
- Scheduler orchestration layer çağırmalıdır.
- Scheduler config `application.yaml` və properties class-lardan gəlməlidir.

### `orchestrator`

Scraping run-ların koordinasiyası burada saxlanılır.

Əsas class:

- `ScrapeJobOrchestrator`

Məsuliyyət:

- enabled `Source` və `Keyword` siyahısını götürmək
- language compatibility yoxlamaq
- date range hesablamaq
- `ScrapeJob` yaratmaq
- job status transition-larını idarə etmək
- `ScraperRunner` çağırmaq

Qayda:

- Bir job uğursuz olsa, digər job-lar dayanmalı deyil.
- Daily previous-day flow duplicate job yaratmamalıdır.

### `scraper`

Scraper runtime contract və adapter-lər burada saxlanılır.

Struktur:

```text
scraper
├── ScraperRunner.java
├── ScraperResult.java
├── EngineScraperRunner.java
├── adapter
├── engine
├── registry
└── support
```

### `scraper.engine`

Scraper engine contract və execution modelləri burada saxlanılır.

Əsas class-lar:

- `ScraperEngine`
- `ScraperExecutionContext`
- `ScraperExecutionResult`
- `ScraperExecutionStatus`
- `ScraperExecutionException`

Qayda:

- `ScraperEngine` DB-yə yazmamalıdır.
- `ScraperEngine` `ScrapeJob` yaratmamalıdır.
- `ScraperEngine` yalnız adapter seçir və scrape nəticəsini qaytarır.

### `scraper.registry`

Adapter registry burada saxlanılır.

Əsas class:

- `ScraperAdapterRegistry`

Məsuliyyət:

- bütün `ScraperAdapter` bean-lərini toplamaq
- source üçün uyğun adapter tapmaq
- adapter tapılmadıqda unsupported adapter qaytarmaq

### `scraper.support`

Generic scraper utility-lər burada saxlanılır.

Əsas class-lar:

- `UrlNormalizer`
- `TextHashGenerator`
- `DateRangeValidator`
- `ScraperClock`

Qayda:

- Source-specific parsing burada olmamalıdır.
- Source-specific parser-lər adapter paketində saxlanmalıdır.

### `scraper.adapter`

Hər source və platforma üçün adapter-lər burada saxlanılır.

Generic adapter contract-lar:

- `ScraperAdapter`
- `NewsScraperAdapter`
- `UnsupportedSourceScraperAdapter`

News adapter paketləri:

- `bakuws`
- `oxuaz`
- `mediaaz`
- `onenews`
- `haqqinaz`
- `caliberaz`
- `qafqazinfoaz`
- `lentaz`

Social adapter paketləri:

- `xcom`
- `facebook`
- `instagram`
- `tiktok`

Qayda:

- Hər source üçün ayrıca paket açılmalıdır.
- Selector-lar ayrıca `*Selectors` class-da saxlanmalıdır.
- Date parsing ayrıca `*DateParser` class-da saxlanmalıdır.
- Adapter DB-yə yazmamalıdır.
- Adapter `ScrapedPostDTO` qaytarmalıdır.
- Category yığılmamalıdır, əgər ayrıca tələb yoxdursa.
- Media bütün səhifədən yox, article/post scope daxilindən yığılmalıdır.

### `tools`

Manual authentication və local utility class-lar burada saxlanılır.

Cari platforma auth generator-ları:

- `tools.xauth`
- `tools.facebookauth`
- `tools.instagramauth`
- `tools.tiktokauth`

Qayda:

- Password, token, cookie və storage state content log edilməməlidir.
- Storage state faylları Git-ə commit edilməməlidir.
- Interactive login official browser flow ilə aparılır.

### `util`

Kiçik ümumi helper-lər üçün istifadə olunur.

Qayda:

- Böyük business logic buraya qoyulmamalıdır.
- Əgər helper scraper-specific-dirsə, `scraper.support` daha uyğundur.

## Resource strukturu

Əsas resource faylları:

```text
src/main/resources
├── application.yaml
└── db
    └── migration
```

### `application.yaml`

Bütün əsas konfiqurasiyalar burada env variable default-ları ilə saxlanılır.

Qayda:

- `ddl-auto=validate` qalmalıdır.
- Schema Flyway migration-larla idarə olunmalıdır.
- Secret-lər hardcoded olmamalıdır.
- Local default-lar `${ENV_NAME:default}` formatında verilməlidir.

### `db/migration`

Flyway migration-ları burada saxlanılır.

Qayda:

- Yeni schema dəyişikliyi mütləq yeni migration ilə edilməlidir.
- Mövcud migration faylları artıq tətbiq olunubsa dəyişdirilməməlidir.
- Migration adları ardıcıl olmalıdır:

```text
V20__short_description.sql
V21__another_change.sql
```

## Test strukturu

Test kodları burada yerləşir:

```text
src/test/java/org/raul/javawebscarper
```

Qayda:

- Yeni service logic üçün unit test yazılmalıdır.
- Adapter support/parser logic testlərlə örtülməlidir.
- Date parser-lər üçün ayrıca test olmalıdır.
- Authentication state və cookies üçün real secret test data istifadə edilməməlidir.
- Synthetic empty state istifadə edilə bilər:

```json
{
  "cookies": [],
  "origins": []
}
```

## API response qaydası

Bütün API response-lar `BaseResponseDTO` ilə qaytarılır.

Uğurlu cavab:

```json
{
  "success": true,
  "message": "optional message",
  "data": {},
  "timestamp": "2026-07-23T10:00:00+04:00"
}
```

Xəta cavabı:

```json
{
  "success": false,
  "message": "Validation failed",
  "timestamp": "2026-07-23T10:00:00+04:00",
  "errors": {
    "fieldName": ["message"]
  }
}
```

## Əsas endpoint qrupları

CRUD endpoint-lər:

- `/api/sources`
- `/api/keywords`
- `/api/authors`
- `/api/posts`
- `/api/scrape-jobs`

Manual scraping trigger-lər:

- `POST /api/scrape-jobs/run-scheduled`
- `POST /api/scrape-jobs/run-daily-previous-day`

Monitoring və docs:

- `/actuator/health`
- `/v3/api-docs`
- `/swagger-ui.html`

## ScrapeJob status və run type

Status-lar:

- `PENDING`
- `RUNNING`
- `SUCCESS`
- `FAILED`

Run type-lar:

- `MANUAL`
- `SCHEDULED`
- `DAILY_PREVIOUS_DAY`

Qayda:

- Daily previous-day flow eyni `source + keyword + dateFrom + dateTo + runType` üçün duplicate job yaratmamalıdır.
- Unsupported adapter və auth/challenge failure-lar `FAILED` kimi qeyd olunmalıdır.

## Source və Keyword language siyasəti

`Keyword` bir `Language` dəyərinə sahibdir.

`Source` birdən çox `supportedLanguages` dəyəri saxlaya bilər.

Qayda:

- Scheduler yalnız `source.supportedLanguages contains keyword.language` olduqda job yaratmalıdır.
- CRUD ilə `ScrapeJob` yaradılarkən source keyword dilini dəstəkləmirsə, request reject olunmalıdır.

Mövcud language dəyərləri:

- `AZ`
- `RU`
- `EN`
- `TR`

## Adapter əlavə etmə qaydası

Yeni source adapter əlavə ediləndə adətən bunlar lazımdır:

```text
src/main/java/.../scraper/adapter/<sourcecode>
├── <Source>DateParser.java
├── <Source>NewsScraperAdapter.java
├── <Source>ScraperSupport.java
├── <Source>SearchResultCard.java
└── <Source>Selectors.java
```

Test-lər:

```text
src/test/java/.../scraper/adapter/<sourcecode>
├── <Source>DateParserTests.java
├── <Source>NewsScraperAdapterTests.java
└── <Source>ScraperSupportTests.java
```

Migration:

```text
src/main/resources/db/migration/Vxx__seed_<source>_source.sql
```

Qayda:

- `Source.code` sabit və uppercase olmalıdır.
- `baseUrl` source üçün canonical olmalıdır.
- News saytlarında author adətən saytın özüdür.
- Article text yalnız content scope-dan çıxarılmalıdır.
- Ads, related posts, share blocks, footer və sidebar text-ə düşməməlidir.
- Media yalnız post/article scope daxilindən götürülməlidir.

## Authentication və storage state qaydaları

X, Facebook, Instagram və TikTok üçün Playwright storage state istifadə oluna bilər.

Qadağandır:

- login/password commit etmək
- cookie commit etmək
- storage state JSON commit etmək
- token, session id və localStorage dəyərlərini log etmək
- CAPTCHA bypass etmək
- private/reverse-engineered API istifadə etmək

`.gitignore` storage state və auth fayllarını ignore etməlidir.

## Lokal işə salma

`.env` faylı lazımdırsa nümunədən yarat:

```powershell
copy .env.example .env
```

PostgreSQL üçün:

```powershell
docker compose up -d postgres
```

PostgreSQL hazır olduğunu yoxlamaq üçün:

```powershell
docker compose ps
```

IDE və ya Gradle ilə run edəndə app default olaraq `localhost:5432`-yə qoşulur. Ona görə əvvəl `postgres` servisi yuxarıdakı komanda ilə qalxmalıdır.

Testlər üçün:

```powershell
.\gradlew.bat test
```

Build üçün:

```powershell
.\gradlew.bat clean build
```

App run üçün:

```powershell
.\gradlew.bat bootRun
```

App-i Docker Compose ilə run edəndə:

```powershell
docker compose up -d app
```

Bu rejimdə app database-ə `DB_HOST=postgres` ilə qoşulur, çünki app və PostgreSQL eyni Docker network-dədir.

Playwright Chromium install üçün:

```powershell
.\gradlew.bat playwrightInstall
```

## Fayllar və Git ignore siyasəti

Commit edilməlidir:

- `src/main/java`
- `src/test/java`
- `src/main/resources`
- `docs`
- `build.gradle`
- `settings.gradle`
- `compose.yaml`
- `Dockerfile`
- `.env.example`
- `.gitignore`

Commit edilməməlidir:

- `.env`
- `.env.*`
- `.idea`
- `.gradle`
- `build`
- `logs`
- `screenshots`
- `debug-html`
- `playwright/.auth`
- `*storage-state*.json`
- `*auth-state*.json`
- cookies və session dump faylları

## Branch siyasəti

Hər task ayrıca branch-də edilməlidir.

Branch adları aşağıdakı prefix-lərlə başlamalıdır:

- `feature/` - yeni funksionallıq
- `fix/` - bug fix
- `refactor/` - struktur və kod təmizliyi
- `docs/` - sənədləşmə
- `test/` - test dəyişiklikləri
- `chore/` - texniki və köməkçi dəyişikliklər

Nümunələr:

```text
feature/new-source-adapter
fix/daily-job-deduplication
refactor/scraper-adapter-common-flow
docs/project-onboarding
test/post-ingestion-deduplication
chore/update-gradle-wrapper
```

Qayda:

- `main` üzərində birbaşa development aparılmamalıdır, yalnız xüsusi hallarda maintainer qərarı ilə.
- Böyük task-lar ayrıca logical branch-lərə bölünməlidir.
- Bir branch-də unrelated dəyişikliklər qarışdırılmamalıdır.
- Revert edilmiş merge-dən sonra eyni branch-i yenidən merge etmək problem yarada bilər. Belə halda yeni reapply branch yaradılıb commit-lər cherry-pick edilməlidir.

## Commit siyasəti

Commit mesajları aşağıdakı convention-a uyğun olmalıdır:

```text
type: short description
```

İcazəli type-lar:

- `feat` - yeni funksionallıq
- `fix` - bug fix
- `docs` - sənədləşmə
- `style` - logic dəyişməyən format/code style
- `refactor` - struktur və kodun yenilənməsi
- `perf` - performans yaxşılaşdırması
- `test` - test əlavə və ya düzəlişi
- `chore` - texniki yardımçı dəyişikliklər

Nümunələr:

```text
feat: add tiktok scraper adapter
fix: prevent duplicate daily scrape jobs
docs: add project onboarding guide
refactor: extract scraper date range resolver
test: cover media az article extraction
chore: update docker compose defaults
```

Qayda:

- Commit-lər kiçik və logical olmalıdır.
- Bir commit bir mənalı məqsəd daşımalıdır.
- Secret, storage state, cookies və local debug artifacts commit edilməməlidir.
- Commit etməzdən əvvəl `git status` yoxlanmalıdır.
- Qarışıq worktree varsa, yalnız lazımi fayllar stage edilməlidir.

## Pull Request qaydası

PR açmazdan əvvəl:

```powershell
git status
.\gradlew.bat test
.\gradlew.bat clean build
```

PR description daxil etməlidir:

- nə dəyişdi
- niyə dəyişdi
- necə yoxlanıldı
- migration varsa, DB təsiri
- manual QA nəticəsi
- known limitations

## Manual QA checklist

Dəyişiklik tipindən asılı olaraq yoxlanmalıdır:

- app start olur
- Flyway migration keçir
- JPA validate uğurludur
- Swagger açılır
- `/actuator/health` `UP` qaytarır
- əsas CRUD endpoint işləyir
- scheduler disabled/enabled halda gözlənilən davranır
- manual scrape trigger işləyir
- yeni adapter uyğun source üçün seçilir
- duplicate post/media/postKeyword yaranmır
- auth state və secret log edilmir

## Təhlükəsizlik qaydaları

- Real login/password heç vaxt repository-yə düşməməlidir.
- `.env` faylları commit edilməməlidir.
- Storage state şəxsi session kimi qəbul edilməlidir.
- Cookie və token dəyərləri heç vaxt log edilməməlidir.
- CAPTCHA və verification avtomatik bypass edilməməlidir.
- Private API reverse engineering istifadə edilməməlidir.
- Debug HTML və screenshot-lar commit edilməməlidir.

## Mövcud sənədlər

`docs` paketində əlavə platforma sənədləri var:

- `facebook-authentication.md`
- `facebook-extension-analysis.md`
- `instagram-authentication.md`
- `instagram-extension-analysis.md`
- `tiktok-authentication.md`
- `tiktok-extension-analysis.md`
- `x-authentication.md`
- `x-extension-analysis.md`
- `manual-db-cleanup.md`

Bu sənədlər platforma-specific onboarding və manual QA üçün istifadə olunur.

## Qısa development flow

Yeni task üçün tövsiyə olunan flow:

```powershell
git switch main
git pull --ff-only
git switch -c feature/task-name

# dəyişiklikləri et
.\gradlew.bat test
.\gradlew.bat clean build

git status
git add <specific-files>
git commit -m "feat: short description"
git push -u origin feature/task-name
```

Sonra GitHub-da PR açılır.

## Əsas prinsip

Layihədə əsas prinsip belədir:

- domain modeli təmiz qalır
- API DTO ilə danışır
- business logic service layer-dədir
- scraper adapter yalnız scrape edir
- ingestion DB-yə yazır
- scheduler yalnız trigger edir
- migration schema-nı idarə edir
- secret və local state Git-ə düşmür
