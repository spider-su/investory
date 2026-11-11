# Investory document RAG (initial milestone)

Separate, read-only Python service for question answering over Investory Markdown documentation.
Uses LangChain, OpenAI embeddings, ChromaDB, FastAPI, and OpenAI chat generation.
It does **not** query portfolios, perform financial calculations, or expose private financial data.

## Local setup

From `services/rag`:

```sh
python3.12 -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"
cp .env.example .env
# Set OPENAI_API_KEY in .env (or environment); do not commit it.
investory-rag-index
uvicorn investory_rag.main:app --host 127.0.0.1 --port 8090
```

The default `RAG_DOCS_ROOT=../../docs` indexes Investory's checked-in Markdown
contracts, including retirement and portfolio-accounting documentation.
Run indexing explicitly after changes; it reuses unchanged chunks, embeds new or modified chunks, and removes obsolete chunks. Run only one indexing process at a time.
Do not point this at confidential statements or exported brokerage data.

```sh
curl -s http://127.0.0.1:8090/health
curl -s http://127.0.0.1:8090/ask \
  -H 'Authorization: Bearer YOUR_RAG_API_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"question":"What is the Reserve + Harvest strategy?"}'
pytest -q
```

`POST /ask` returns `{"answer": "...", "sources": ["domain/retirement-simulation.md"]}`.
If the collection is empty, it returns a useful message without calling the LLM.

## Boundaries and next steps

- Do not expose port 8090 publicly. The service requires a shared internal bearer token for /ask, but has no end-user/portfolio authorization. Restrict networking and authorize users in Java before financial integration.
- Indexing calls the OpenAI embeddings API and incurs usage charges. Questions
  call the OpenAI chat and embeddings APIs; selected excerpts leave the host.
- Chroma persists locally under `data/chroma`; keep the volume persistent
  during deployments. Back it up or re-index from source documentation.
- Indexing uses stable per-chunk identifiers. An unchanged document reuses existing embeddings, while edited and deleted chunks are reconciled. Do not run concurrent indexers.
- Changing the embedding model requires rebuilding its collection; do not reuse vectors from a different embedding model.
- Retrieval uses a configurable minimum relevance threshold, not a calibrated confidence measure. Evaluate and tune it against representative questions before relying on automated answers.
- Retrieved text is untrusted. The service prompts the model to use sources
  only, but source citations and answers must still be reviewed.
- Follow-up work: source-aware retrieval evaluation, auth, request budgets, telemetry, Java read-only fact tools and MCP integration.

## Retrieval configuration

The default `RAG_TOP_K=4` limits retrieved chunks. `RAG_MIN_RELEVANCE_SCORE=0.3` filters low-scoring results; Chroma/LangChain relevance scores are **not calibrated probabilities**. Tune the cutoff after running evaluation questions against your own documentation. `RAG_INDEX_BATCH_SIZE=64` controls embedding batch size. If no chunks pass the threshold, `/ask` returns an explicit insufficient-evidence answer without calling the chat model.

## Container build

From repository root:

```sh
docker build -f services/rag/Dockerfile -t investory-rag .
```

Mount documentation and a persisted data directory and explicitly run the
`investory-rag-index` command before starting the API.
Use environment variables for API credentials.

## Retrieval benchmark

After running `investory-rag-index`, execute `investory-rag-eval --cases eval/cases.json` to measure source-hit rate against expected Investory documentation. This makes real embedding API calls but never calls the chat model. Use `--min-hit-rate 0.8` to fail a manually triggered run below an explicitly chosen target; the default simply reports. The bundled examples verify the withdrawal strategy, portfolio accounting and planning timeline. They are a *starting benchmark*, not a claim that current retrieval accuracy meets a target. Add more positive, negative and ambiguous questions before deploying to real users. CI only runs deterministic tests with fakes, not paid embedding requests.

## Java integration boundary

The Spring Boot `GET /api/v1/portfolios/{portfolioId}/profile` endpoint returns private, portfolio-scoped financial data. Do not have this standalone Python service fetch it with user-provided IDs or a service-wide token: authorization needs to happen in Java for the current authenticated user first. A later integration can have Java retrieve a minimally scoped, read-only financial facts snapshot using its domain services, then pass that snapshot to an internal authenticated RAG orchestration endpoint. Never embed those snapshots in ChromaDB, persist them in prompts/logs, or treat documentation retrieval as a source of live financial balances. The initial `/ask` remains strictly documentation-only and must stay on loopback/private networking until authentication is implemented.

## Internal API security and limits

`POST /ask` now **fails closed** (503) when `RAG_API_TOKEN` is missing and returns 401 for missing/incorrect `Authorization: Bearer ...`. `GET /health` remains unauthenticated and contains no financial information. Generate a high-entropy token using a secret manager, never commit it or reuse a user login token. The token identifies an internal caller only; **it does not authorize end users or portfolio access**. Do not expose the service to the public Internet. Requests are limited to `RAG_MAX_CONCURRENT_REQUESTS` (default 1) per process, returning 429 when busy. Use a single worker, private network, and ingress-level rate limits for production. OpenAI operations use a configured 20-second per-request timeout and one retry; this is not a strict overall request deadline. Do not log questions, response bodies, financial statements, tokens, or OpenAI payloads.

## Index safety

Index operations take an advisory non-blocking POSIX lock in the Chroma data directory; concurrent writers on the same shared filesystem fail rather than race. Changed chunks are added before obsolete chunks are deleted, so if embedding fails, existing chunks are retained; indexing is **not fully transactional** and failed runs may leave extra chunks until the next successful run. Avoid simultaneous indexing and serving during reindex, or provide separate generation-based collections before promoting a revision. Persist the directory and mount it writable only to the indexer. The default Docker container runs as an unprivileged user; the image excludes docs, which must be mounted separately (`RAG_DOCS_ROOT=/app/docs`).

## Validation gates

The expanded `eval/cases.json` contains twelve domain questions. Unit tests and a deterministic Chroma vector-store smoke test run in CI without OpenAI credentials, alongside a Docker image build/HTTP smoke check. For **real** acceptance, set a private `OPENAI_API_KEY`, index checked-in docs, then run `investory-rag-eval --min-hit-rate 0.8`; report actual hit rate and investigate misses. Real evaluation is intentionally not executed on public PR CI and is not claimed to have passed. Add negative, adversarial and answer-grounding cases after calibrating the similarity threshold; source-hit rate does not guarantee answer correctness.
