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
Run indexing explicitly after changes; it replaces the previous snapshot.
Do not point this at confidential statements or exported brokerage data.

```sh
curl -s http://127.0.0.1:8090/health
curl -s http://127.0.0.1:8090/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"What is the Reserve + Harvest strategy?"}'
pytest -q
```

`POST /ask` returns `{"answer": "...", "sources": ["domain/retirement-simulation.md"]}`.
If the collection is empty, it returns a useful message without calling the LLM.

## Boundaries and next steps

- Do not expose port 8090 publicly: this MVP has no authentication. Use loopback
  only; authenticate/authorize before integration with Java or MCP.
- Indexing calls the OpenAI embeddings API and incurs usage charges. Questions
  call the OpenAI chat and embeddings APIs; selected excerpts leave the host.
- Chroma persists locally under `data/chroma`; keep the volume persistent
  during deployments. Back it up or re-index from source documentation.
- The index command replaces the collection content rather than incrementally
  updating changed documents. Do not run concurrent indexers.
- Retrieved text is untrusted. The service prompts the model to use sources
  only, but source citations and answers must still be reviewed.
- Follow-up work: source-aware eval tests, ranking/abstention thresholds,
  selective re-indexing, auth, request budgets, telemetry, Java read-only
  fact tools and MCP integration.

## Container build

From repository root:

```sh
docker build -f services/rag/Dockerfile -t investory-rag .
```

Mount documentation and a persisted data directory and explicitly run the
`investory-rag-index` command before starting the API.
Use environment variables for API credentials.
