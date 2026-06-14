# investory

## Docker

Build image locally:

```powershell
Set-Location "E:\projects\investory"
docker build -t aserobaba/investory:latest .
```

Push image to Docker Hub:

```powershell
docker login
docker push aserobaba/investory:latest
```

## Docker Compose

Start app with environment from `.env`:

```powershell
Set-Location "E:\projects\investory"
docker compose up -d
```

Stop and remove containers:

```powershell
Set-Location "E:\projects\investory"
docker compose down
```

## GitHub Actions (Docker publish)

Workflow file: `.github/workflows/docker-publish.yml`

Set repository secrets before running the workflow:

- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN` (Docker Hub access token)

## Broker import API

Generic endpoint:

```powershell
curl -X POST "http://localhost:8080/import/broker/xtb" `
  -F "file=@account_51499241_en_xlsx_2026-04-30_2026-05-31.xlsx" `
  -F "source=MANUAL"
```

`/import/broker/ibkr` is wired but not implemented yet and currently returns an error.

