# Dev Container

The repository includes a reproducible development environment under `.devcontainer/`.

It provides:

- Java 25 and Maven
- PostgreSQL 17
- PostgreSQL command-line tools
- persistent Maven dependency cache
- persistent development database
- Docker access for Testcontainers
- forwarded application and database ports

The environment is intended for VS Code Dev Containers, GitHub Codespaces, DevPod, and other tools that implement the Dev Container specification.

## Requirements

For local use, install:

- Docker
- Visual Studio Code
- the VS Code `Dev Containers` extension

Docker must be running before the container is opened.

## Open the project

1. Clone the repository.
2. Open the repository folder in Visual Studio Code.
3. Run `Dev Containers: Reopen in Container` from the command palette.
4. Wait for the image, PostgreSQL service, and Maven dependency cache to initialize.

The repository is mounted at:

```text
/workspaces/investory
```

The application container connects to PostgreSQL using:

```text
jdbc:postgresql://db:5432/investory
```

The default development credentials are:

```text
database: investory
username: investory
password: investory
```

These credentials are development-only and must not be reused in production.

## Run Investory

Inside the container terminal:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Open:

```text
http://localhost:8080
```

Port `8080` is forwarded automatically. PostgreSQL is also forwarded to a random available host port. Use the VS Code **Ports** view to see the selected host port.

## Build and test

```bash
mvn test
mvn clean package
mvn spotless:check
```

Testcontainers uses the host Docker daemon through the `docker-outside-of-docker` Dev Container feature. Verify access with:

```bash
docker version
```

## Connect to PostgreSQL

From the application container:

```bash
psql -h db -U investory -d investory
```

The password is `investory`.

Useful checks:

```sql
SELECT current_database();
SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'investory';
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

## Reset the development database

The database is stored in the named Docker volume `postgres-data`, so rebuilding the application container does not remove data.

To remove the database and start from an empty state:

1. Close the Dev Container.
2. From the repository root on the Docker host, run:

```bash
docker compose -f .devcontainer/compose.yml down -v
```

3. Reopen the project in the Dev Container.

Flyway will recreate the `investory` schema when the application starts.

## Rebuild after configuration changes

After changing `.devcontainer/Dockerfile`, `.devcontainer/compose.yml`, or `.devcontainer/devcontainer.json`, run:

```text
Dev Containers: Rebuild Container
```

Use `Dev Containers: Rebuild Container Without Cache` when the base image or installed operating-system packages must be downloaded again.

## Environment variables and secrets

The container sets only local database variables. Optional integrations remain disabled or empty by default.

Provide secrets through the host environment, a local uncommitted environment file, or the remote environment's secret manager. Do not commit values for:

- `TWELVEDATA_API_KEY`
- `EXCHANGERATE_API_KEY`
- `TELEGRAM_BOT_TOKEN`
- `OPENAI_API_KEY`
- production database credentials

## Parallel agent work

Use one Git branch or Git worktree per agent. For changes that mutate database state, avoid running multiple agents against the same persistent Dev Container database.

For strict isolation, create a separate Dev Container instance for each worktree. Each instance receives its own Compose project and PostgreSQL volume when the client assigns a unique project name.

Recommended sequence:

```text
investigation -> implementation -> tests -> independent review -> pull request
```

Do not allow two implementation agents to modify the same branch concurrently.

## Files

- `.devcontainer/devcontainer.json`: editor integration, forwarded ports, environment, extensions, and Docker feature
- `.devcontainer/compose.yml`: application and PostgreSQL services
- `.devcontainer/Dockerfile`: Java development image and PostgreSQL client
