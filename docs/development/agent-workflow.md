# Agent Dev Container Workflow

Use one Git worktree and one Dev Container instance per GitHub issue. Run the agent on a dedicated VM; the Dev Container uses the host Docker daemon for Testcontainers and must not share a Docker host with production workloads.

## Requirements

- Docker Engine
- Dev Container CLI (`@devcontainers/cli`)
- a dedicated Git worktree for the issue

Do not expose production credentials, production databases, SSH keys with broad access, or unrelated host directories to the Dev Container.

## Start an isolated issue environment

From the issue worktree:

```bash
bash scripts/agent-devcontainer.sh up 142
```

The helper assigns a distinct Compose project name (`investory-issue-142`). This isolates the application container, PostgreSQL container, Maven cache, and PostgreSQL volume from other issue environments.

## Run the complete validation set

```bash
bash scripts/agent-devcontainer.sh validate 142
```

This runs:

```text
./mvnw -B spotless:check
./mvnw -B test
./mvnw -B package -DskipTests
```

The orchestrator should call the validation helper after every implementation or fix attempt. It should treat a non-zero exit code as a failed attempt and include the command output in the next coding-agent request.

## Remove the environment

After the issue is complete or abandoned:

```bash
bash scripts/agent-devcontainer.sh down 142
```

This removes containers and issue-specific volumes, including the development database. Do not reuse an issue database for another task.

## Suggested orchestration flow

```text
create worktree
-> start issue Dev Container
-> implement one step
-> run validation
-> fix and retry when required
-> commit
-> create pull request
-> remove issue Dev Container and volumes
```

The LLM and GitHub credentials should remain in the external orchestrator. The Investory Dev Container only needs repository files, local development database credentials, build tools, and test infrastructure.
