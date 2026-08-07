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

The environment follows the Dev Container specification and can be used from IntelliJ IDEA, JetBrains Remote Development, VS Code, GitHub Codespaces, DevPod, and compatible clients.

## Requirements

For local IntelliJ IDEA use, install:

- Docker Desktop or Docker Engine
- IntelliJ IDEA with Dev Containers support
- the Docker plugin, when it is not already enabled

Docker must be running before the container is created.

## Open locally with IntelliJ IDEA

### From an already cloned project

1. Clone the repository and check out the required branch.
2. Open the repository normally in IntelliJ IDEA.
3. Open `.devcontainer/devcontainer.json` in the editor.
4. Click the Dev Container gutter action next to the file.
5. Select **Create Dev Container and Mount Sources**.
6. Select the IntelliJ IDEA backend and build the container.
7. Connect after the build finishes.

Mounting sources keeps the working tree on the host. Changes made in the container and on the host use the same files.

### From the IntelliJ IDEA welcome screen

1. Select **Remote Development**.
2. Select **Create Dev Container**.
3. Choose the local Docker connection.
4. Select either the local project or the Git repository.
5. Let IntelliJ detect `.devcontainer/devcontainer.json`, or specify it manually.
6. Select the IntelliJ IDEA backend.
7. Click **Build Container and Continue**.

For IntelliJ IDEA 2025.3 or newer, the project can be opened natively in the same IDE window. This is controlled by:

```text
Settings | Advanced Settings | Dev Containers | Open devcontainer projects natively
```

Older or remote workflows open the project through JetBrains Client.

## Open on a remote Docker host

IntelliJ IDEA can build the Dev Container on a remote Linux machine over SSH.

1. Ensure Docker is installed on the remote machine.
2. Open the IntelliJ IDEA welcome screen.
3. Select **Remote Development** and then **Create Dev Container**.
4. Configure an SSH connection to the remote Docker host.
5. Select the Git repository or the remote path to `.devcontainer/devcontainer.json`.
6. Select the IntelliJ IDEA backend.
7. Build and connect.

This is the recommended setup for a persistent Investory agent or remote development machine.

## Container layout

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

Open the IntelliJ terminal inside the Dev Container and run:

```bash
mvn spring-boot:run
```

The container supplies `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` for its PostgreSQL service. It also
sets `SPRING_PROFILES_ACTIVE=local` as a development convention; datasource selection comes from the
`DB_*` variables and does not require the profile.

Open:

```text
http://localhost:8080
```

Port `8080` is forwarded by the Dev Container client. PostgreSQL port `5432` is also exposed for database tools.

## IntelliJ run configuration

A Maven run configuration can be created with:

```text
Command line: spring-boot:run
Working directory: project root
```

The environment already supplies:

```text
DB_URL=jdbc:postgresql://db:5432/investory
DB_USERNAME=investory
DB_PASSWORD=investory
SPRING_PROFILES_ACTIVE=local
```

Run the configuration using the JDK and Maven available inside the Dev Container, not host installations.

## Connect IntelliJ Database tools

Create a PostgreSQL data source with:

```text
Host: db
Port: 5432
Database: investory
User: investory
Password: investory
```

Use `db` as the host when the database tool runs inside the Dev Container backend.

When connecting from the host IDE instead, use `localhost` and the host port assigned to PostgreSQL by Docker or the Dev Container client.

From the container terminal:

```bash
psql -h db -U investory -d investory
```

Useful checks:

```sql
SELECT current_database();
SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'investory';
SELECT * FROM investory.flyway_schema_history ORDER BY installed_rank;
```

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

## Reset the development database

The database is stored in the named Docker volume `postgres-data`, so rebuilding the application container does not remove data.

To remove the database and start from an empty state:

1. Stop or close the Dev Container.
2. From the repository root on the Docker host, run:

```bash
docker compose -f .devcontainer/compose.yml down -v
```

3. Build or reopen the Dev Container.
4. Start Investory. Flyway recreates the `investory` schema.

## Rebuild after configuration changes

After changing `.devcontainer/Dockerfile`, `.devcontainer/compose.yml`, or `.devcontainer/devcontainer.json`:

1. Open `.devcontainer/devcontainer.json`.
2. Use the Dev Container gutter action.
3. rebuild the Dev Container.

The exact action wording can vary by IntelliJ IDEA version. Existing containers can also be managed from the Dev Containers or Services view.

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

For strict isolation, create a separate Dev Container instance for each worktree. Each instance should use a distinct Docker Compose project and PostgreSQL volume.

Recommended sequence:

```text
investigation -> implementation -> tests -> independent review -> pull request
```

Do not allow two implementation agents to modify the same branch concurrently.

## Other compatible clients

The `customizations.vscode` section in `devcontainer.json` is ignored by IntelliJ IDEA. It only installs recommended extensions when the same container is opened from VS Code.

## Files

- `.devcontainer/devcontainer.json`: Dev Container integration, ports, environment, and Docker access
- `.devcontainer/compose.yml`: application and PostgreSQL services
- `.devcontainer/Dockerfile`: Java development image and PostgreSQL client
