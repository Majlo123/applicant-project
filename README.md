# Person management

Person management application using Next.js App Router, Spring Boot, Hibernate and locally installed PostgreSQL. Run all services natively; no container tooling is required.

You receive an existing Person management application. Run it locally, compare CRUD and validation behavior with the assignment specification, investigate discrepancies, implement focused fixes and refactor one small area. Verify results after refreshing the application and through relevant automated checks. Submit your changes and concise notes within three days of receiving the assignment.

## Run locally

The tested platform is Windows with PowerShell, Node.js 24.22.3 (pinned in `frontend/.nvmrc`), JDK 25.0.4.1, PostgreSQL 15.18 and Maven 3.9.9 via the supplied Wrapper. Set `JAVA_HOME` to the JDK directory and add its `bin` and PostgreSQL's `bin` to `PATH`. Use a JDK, not just a JRE. Install PostgreSQL as a local Windows service with UTF-8 support and retain the administrator password chosen during installation. Start it with Services or, from an administrator terminal, `Start-Service postgresql-x64-15`.

From the repository root:

```powershell
Copy-Item .env.example .env
# Set APP_DB_PASSWORD in .env to a new local application password.
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-25'
$env:PGUSER='postgres'
.\scripts\setup-db.ps1
.\scripts\start.ps1
```

Setup prompts for the PostgreSQL administrator password unless a standard PostgreSQL credential source is already configured. It creates dedicated application and test databases and an unprivileged login; it does not change an existing role's password or take ownership of existing databases. PostgreSQL administrator credentials do not belong in `.env` or Git.

`start.ps1` loads the root `.env`, checks runtimes, ports and database connectivity, installs locked frontend dependencies, builds and starts the backend, waits for readiness after migrations, and starts Next.js. Open http://127.0.0.1:3000/persons. Ctrl+C stops the two applications and leaves the installed PostgreSQL service running. Startup logs are in ignored `.logs/`.

Defaults are PostgreSQL `127.0.0.1:5432`, backend `127.0.0.1:8080`, frontend `127.0.0.1:3000`. If Windows reserves port 8080, configure a free `APP_BACKEND_PORT` and matching `BACKEND_URL` in `.env`. All browser API calls use relative `/api/persons` URLs. The server proxies `/api/:path*` to the backend, retaining `/api`. Restart the development server after proxy changes; rebuild before restarting a production server. Database credentials are server-only and never use a `NEXT_PUBLIC_` prefix.

The root scripts explicitly load `.env`; Spring Boot does not load it automatically. To use separate terminals, run `node scripts/native.mjs backend` and `node scripts/native.mjs frontend` from the repository root. These commands also load `.env`. If running `npm run dev` directly inside `frontend`, set `BACKEND_URL` in that terminal or a local `frontend/.env.local`; it defaults to port 8080. After manually exporting the root environment in a terminal, the direct backend command from `backend` is `.\mvnw.cmd spring-boot:run`.

The Maven Wrapper downloads Maven on first use, so network access is required for initial setup. Configure your normal Maven settings and network proxy if your environment requires them. An optional local-only `APP_MAVEN_SETTINGS` path in `.env` lets the root scripts use an existing Maven settings file. Never commit credentials or network-specific settings.

Flyway owns schema creation; Hibernate validates it with Open Session in View disabled. A fresh demo setup seeds Anna Novak, Daniel Meyer and Sara Ali in that order. Restarts preserve edits and deletions, including deletion of all records. To restore the local demo, run `.\scripts\reset-demo.ps1` and type the exact confirmation. Reset is restricted to the local `person_training` database.

The `.sh` launchers invoke the same native Node.js orchestration and may also be used with Git Bash on Windows: `./scripts/setup-db.sh`, `./scripts/start.sh`, `./scripts/test.sh`, `./scripts/reset-demo.sh`. No Linux or macOS setup has been verified for this delivery.

## Checks

```powershell
# From the repository root, using the separate local test database:
.\scripts\test.ps1

# Backend unit tests only (no database needed), from backend:
.\mvnw.cmd test

# Frontend checks, from frontend:
npm test
npm run typecheck
npm run lint
npm run build
```

`test.ps1` runs backend unit and HTTP integration checks, frontend tests, type checks, lint and a production build. Integration tests refuse the application database and require a distinct local `APP_TEST_DB_NAME` ending in `_test`. They reset only that test fixture. No H2 or container database is substituted. The backend readiness endpoint is `/actuator/health/readiness`.

Public smoke tests are a starting point; coverage is incomplete. Exercise create, view, edit and delete, meaningful boundary values and failed requests. Verify results after refresh. Document your findings and one focused refactoring in `SUBMISSION.md`.

## Expected behavior

Person records contain first name, last name, email and date of birth. The server generates the ID. Names are required after trimming and limited to 100 characters; Unicode, spaces, apostrophes and hyphens are accepted. Email is required, limited to 254 characters and trimmed without changing spelling. The basic email rule is one nonempty whitespace-free local part, `@`, and a whitespace-free domain containing a dot. Duplicate emails are allowed. Birth dates are ISO calendar dates up to and including today in UTC. No minimum age applies.

Routes: `/persons`, `/persons/new`, `/persons/[id]`, `/persons/[id]/edit`. Delete requires confirmation. Successful operations persist across refresh; failed saves preserve input and show feedback.

The API exposes GET/POST `/api/persons` and GET/PUT/DELETE `/api/persons/{id}`. POST returns 201 and a saved record; PUT returns 200; DELETE returns empty 204. Read returns 200. Missing records return 404, invalid input returns 400, unexpected errors return a safe 500. Errors use `{ code, message, fieldErrors }`. List records are ordered by ID.

The company Word assignment was not supplied in this workspace. Place the supplied document at `docs/assignment.docx` before distributing the exercise. No substitute assignment document is included.
