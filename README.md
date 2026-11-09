# CommonGround

**A cleaner place starts with us.** A global community cleanup platform, implemented with an English Angular UI and a Java Spring Boot API.

Report a neglected place with several photos or videos. Anyone can organize its cleanup, volunteers can join, and participants can share the result. Country, city and meeting-point fields support worldwide locations. Cleanup schedules use an explicit IANA time zone and are displayed in each visitor's local time.

## Try the included runnable application

With Java 21 installed, double-click `run.cmd` on Windows, or run `./run.sh` on macOS/Linux. Open **http://localhost:8080** after startup. The included `release/commonground-1.0.0.jar` contains both the built Angular UI and Java API. To change the code, use the development workflow below; rebuild the JAR after edits.

## Run locally (Windows)

Install **JDK 21** (including `javac`) and **Node.js 24 LTS**. Set `JAVA_HOME` to your JDK directory if necessary. The Maven wrapper downloads Maven on first use; internet access is required for initial dependency installation.

Open two terminals in the extracted `commonground-angular-java` folder.

Terminal 1 — Java API:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Terminal 2 — Angular:

```powershell
cd frontend
npm ci
npm start
```

Open **http://localhost:4200**. If PowerShell blocks `npm.ps1`, run `npm.cmd ci` and `npm.cmd start`, or use Command Prompt. On macOS/Linux, use `./mvnw` instead of `.\mvnw.cmd`.

## Build one runnable Java application

From the project root:

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
java -jar backend\target\commonground-1.0.0.jar
```

On macOS/Linux:

```bash
./build.sh
java -jar backend/target/commonground-1.0.0.jar
```

Then open **http://localhost:8080**. The build copies the Angular production bundle into Spring Boot's static resources and runs the Java integration tests. Always launch the JAR from the same directory: the default database and upload paths are relative to the current directory.

Alternatively, with Docker installed:

```bash
docker compose up --build
```

Open http://localhost:8080. Docker stores the database and uploaded media in the `commonground-data` named volume.

## Features

- Reports with country, city, meeting point, description and up to 15 photos/videos per upload (50 MB combined).
- Search by title/location; country and progress filters; My activity.
- Cleanup organization with date, time, explicit time zone, volunteer goal and supplies.
- Join/leave and like/unlike with idempotent server operations.
- Before/after galleries with native video controls and byte-range playback.
- Participant result uploads, with clear distinction between community reports and independent verification.
- Organizer-managed external livestream links with filming-consent confirmation.
- Responsive English UI, labeled forms, keyboard-accessible dialogs, loading/error/empty states, upload previews and locally saved report text drafts.
- Persistent relational data, HttpOnly browser sessions and local filesystem media.

## Identity and livestream scope

This runnable MVP uses an anonymous, signed-in-browser-style identity through a random HttpOnly session cookie. It has **no email/password accounts or cross-device recovery**. Clearing cookies or switching browsers creates another identity; existing report ownership and organizer controls do not transfer. Reports and media are public to visitors of the application. Likes represent browser identities, not verified unique people.

**Livestreaming uses links to YouTube, Facebook, Instagram, TikTok, Twitch or Vimeo.** Start the actual broadcast there, then share its HTTPS link here. CommonGround does not capture the camera, relay video or verify that a linked broadcast is currently live. Ending the live badge does not stop the broadcast on its host platform.

The UI starts empty. There are no fabricated community counts or seeded reports. “CommonGround” is a working product name; domain/trademark availability has not been checked. This source rewrite does not modify the previously hosted Houmti site or import its database.

## Architecture

- `frontend/`: Angular 21, standalone components, signals, reactive forms, typed HTTP service; production output in `dist/commonground/browser`.
- `backend/`: Java 21, Spring Boot 3.5, Spring MVC, JDBC transactions, H2 file database, Bean Validation.
- `VisitorFilter`: server-generated random session token, stored as SHA-256 hash, 1-year expiry; mutation Origin checks.
- `PlaceService`: owner/organizer permissions, transactional state changes and row locking.
- `MediaStorage`: MIME/header checks, UUID filenames, rollback cleanup, streaming downloads.
- `PlatformTest`: MockMvc integration tests for the community lifecycle, ownership, upload validation, byte ranges, timezone conversion and livestream restrictions.

API base `/api`: `GET /health`, `GET /places`, multipart `POST /places`, JSON `POST /places/{id}/edit`, `/cleanup`, `/join`, `/like`, `/live`, multipart `POST /places/{id}/after`, and `GET /media/{id}`. See the typed Angular service and Java request records for payloads. All writes require an allowed `Origin` header; browser fetches send it automatically. Browser cookies are handled automatically on the same origin.

## Configuration

| Variable | Default / purpose |
| --- | --- |
| `PORT` | `8080` |
| `DATABASE_URL` | `jdbc:h2:file:./data/commonground;DB_CLOSE_ON_EXIT=FALSE` |
| `DATABASE_USER` / `DATABASE_PASSWORD` | `sa` / empty, local development |
| `UPLOAD_DIR` | `./data/uploads` |
| `ALLOWED_ORIGINS` | localhost and 127.0.0.1 on ports 4200 and 8080; comma-separated exact origins |
| `SECURE_COOKIES` | `false` locally; set `true` when deployed with HTTPS |

For a hosted instance, serve Angular and `/api` from the same HTTPS origin, set `ALLOWED_ORIGINS` to that exact origin and `SECURE_COOKIES=true`, and persist the data directory. Back up **both** the database and uploads together. Stop the process before taking a simple file-copy backup. H2 is for a single-instance MVP; a PostgreSQL migration requires a driver, schema and query review, not just changing the URL.

Before an open public launch, add recoverable accounts, abuse reporting/moderation, rate limits, upload scanning/transcoding, operational monitoring and a media retention policy. Media checks are basic signature checks, not malware scans. The feed currently loads the newest 200 reports; pagination is not implemented. Expired sessions are ignored but do not have a scheduled database cleanup job yet. The external hero image and optional web fonts require internet access; system-font fallbacks are provided.

## Verify

```bash
cd frontend
npm ci
npm run build
cd ../backend
./mvnw test
```

On Windows replace `./mvnw` with `.\mvnw.cmd`. Tests use an isolated in-memory database and `target/test-uploads`.

Dependency references: https://angular.dev/reference/versions and https://docs.spring.io/spring-boot/3.5/system-requirements.html

## Validation of this package

Angular production build passed. All three Java integration tests passed. A packaged-JAR smoke test verified application startup, the Angular HTML entry point, health and the empty-database API. Browser visual inspection was blocked by the execution environment. Docker and the Windows scripts are provided but were not executed here.
