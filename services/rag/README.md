# Investory document RAG and authorized portfolio explanations

Separate, read-only Python service for question answering over Investory Markdown documentation.
Uses LangChain, configurable local/OpenAI embeddings, ChromaDB, FastAPI, and OpenAI chat generation.
It does not query portfolios or calculate financial values. The optional Java bridge supplies authorized aggregate facts transiently for explanations.

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
- With the OpenAI embedding provider, indexing and retrieval call its embeddings API.
  With the local provider, vectors are generated on CPU without an embedding API key.
  Answer generation still calls OpenAI chat; selected excerpts and authorized facts leave the host.
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

The image bundles documentation; mount a writable data directory and explicitly run the
`investory-rag-index` command before starting the API.
Use environment variables for API credentials.

## Retrieval benchmark

After running `investory-rag-index`, execute `investory-rag-eval --cases eval/cases.json` to measure source-hit rate against expected Investory documentation. This makes real embedding API calls but never calls the chat model. Use `--min-hit-rate 0.8` to fail a manually triggered run below an explicitly chosen target; the default simply reports. The bundled examples verify the withdrawal strategy, portfolio accounting and planning timeline. They are a *starting benchmark*, not a claim that current retrieval accuracy meets a target. Add more positive, negative and ambiguous questions before deploying to real users. CI only runs deterministic tests with fakes, not paid embedding requests.

## Java integration boundary

The Spring Boot `GET /api/v1/portfolios/{portfolioId}/profile` endpoint returns private, portfolio-scoped financial data. Do not have this standalone Python service fetch it with user-provided IDs or a service-wide token: authorization needs to happen in Java for the current authenticated user first. The optional integration has Java retrieve a minimally scoped, read-only financial facts snapshot using its domain services, then pass that snapshot to an internal authenticated RAG orchestration endpoint. Never embed those snapshots in ChromaDB, persist them in prompts/logs, or treat documentation retrieval as a source of live financial balances. The initial `/ask` remains strictly documentation-only and must stay on loopback/private networking behind internal bearer authentication.

## Internal API security and limits

`POST /ask` now **fails closed** (503) when `RAG_API_TOKEN` is missing and returns 401 for missing/incorrect `Authorization: Bearer ...`. `GET /health` remains unauthenticated and contains no financial information. Generate a high-entropy token using a secret manager, never commit it or reuse a user login token. The token identifies an internal caller only; **it does not authorize end users or portfolio access**. Do not expose the service to the public Internet. Requests are limited to `RAG_MAX_CONCURRENT_REQUESTS` (default 1) per process, returning 429 when busy. Use a single worker, private network, and ingress-level rate limits for production. OpenAI operations use a configured 20-second per-request timeout and one retry; this is not a strict overall request deadline. Do not log questions, response bodies, financial statements, tokens, or OpenAI payloads.

## Index safety

Index operations take an advisory non-blocking POSIX lock in the Chroma data directory; concurrent writers on the same shared filesystem fail rather than race. Changed chunks are added before obsolete chunks are deleted, so if embedding fails, existing chunks are retained; indexing is **not fully transactional** and failed runs may leave extra chunks until the next successful run. Avoid simultaneous indexing and serving during reindex, or provide separate generation-based collections before promoting a revision. Persist the directory and mount it writable only to the indexer. The default Docker container runs as an unprivileged user; the image bundles its versioned docs at `/app/docs` and evaluation inputs at `/app/eval`.

## Validation gates

The expanded `eval/cases.json` contains twelve positive domain questions. `eval/exploratory-cases.json` adds six negative/ambiguous questions and can be run separately with `investory-rag-eval --cases eval/exploratory-cases.json`. Its abstention score is exploratory, not a release gate, until the relevance threshold is calibrated. Unit tests and a deterministic Chroma vector-store smoke test run in CI without OpenAI credentials, alongside a Docker image build/HTTP smoke check. For **real** acceptance, set a private `OPENAI_API_KEY`, index checked-in docs, then run `investory-rag-eval --min-hit-rate 0.8`; report actual hit rate and investigate misses. Provider chat evaluation is not executed on public PR CI. The local seeded index is evaluated offline in CI. Add negative, adversarial and answer-grounding cases after calibrating the similarity threshold; source-hit rate does not guarantee answer correctness.

## Authenticated Java portfolio integration

The Spring Boot adapter is **opt-in** with `INVESTORY_RAG_ENABLED=true`; it is disabled
by default. Configure `INVESTORY_RAG_URL=http://127.0.0.1:8090` (replace with the
private service DNS when deployed) and `INVESTORY_RAG_SERVICE_TOKEN` with the
same secret as Python's `RAG_API_TOKEN`. Do not expose Python to the Internet or
store these credentials in Git.

Use the authenticated Investory endpoint:

```bash
curl --fail-with-body -X POST 'http://localhost:8080/api/v1/portfolios/1/rag/ask' \
  -H "Authorization: Bearer $INVESTORY_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"question":"What is my current net worth?"}'
```

Java checks `AuthorizationService.canReadScoped` for the requested portfolio
**before** loading its profile. It reuses `ProfileSnapshotReader` and
`ProfileResponseMapper` and sends only currency, market portfolio value,
long-term assets, net worth, liquid assets and illiquid assets to Python.
Neither broker transactions nor personal identifiers are transmitted. The Python
`/ask-with-facts` endpoint accepts only this allowlist, rejects unrecognized
fields, and generates an answer from transient facts and optional documentation.
Facts are never inserted into ChromaDB. Sources containing live facts use
`live:profile`; documentation sources keep their relative paths.

These aggregates **are transmitted to OpenAI at inference time**, so enable
this integration only after confirming appropriate consent and provider/data
handling policy. Python's service token authenticates Java, **not individual
Investory users**. Do not allow direct client access to `/ask-with-facts`.
Current answers are explanatory and must not be treated as tax or investment
advice. This PR intentionally does not add a browser chat UI or MCP tool.

For bearer or Basic clients the Spring endpoint has a narrowly scoped CSRF
exception. Browser-cookie clients must continue supplying the normal CSRF
token. Java performs role and portfolio checks even when the development
ownership relaxation flag is enabled.

Unavailable aggregate values remain JSON null on the Java-to-Python wire and
are marked `unavailable` in the transient model context; they must not be
interpreted as zero. Java rejects empty, malformed, or incomplete Python
responses through the same sanitized 502 path as upstream HTTP failures.

The Java HTTP adapter test runs a real local HTTP server to check the internal
credential, exact aggregate allowlist, decimal/null serialization, and failure
handling. Python tests exercise fact prompting both with and without indexed
documents and verify that facts do not modify the vector store. Run these with
the normal Maven unit suite and `pytest -q` from this service. These deterministic
checks do not establish real OpenAI answer quality; the retrieval evaluation
and private inference acceptance gates above still apply.


## Release acceptance and deployment

Follow the [staged release plan](../../docs/development/ai-integration-release.md).
Main publication builds and scans both images, runs Python checks before RAG
publication, and records separate receipts. The opt-in GitOps companion binds
only to loopback and indexes the image corpus before serving. Activation requires
a private OpenAI key and successful real retrieval/inference acceptance.

Install `pip install -e ".[acceptance]"` and export `INVESTORY_TOKEN` for a
non-admin profile member. Then run:

```sh
investory-ai-smoke --base-url http://localhost:8080 --portfolio 1 --denied-portfolio 2
```

Use real authorized/unauthorized portfolio IDs. The smoke check uses the official
MCP SDK and calls Java's RAG endpoint. It verifies tool discovery, invocation,
portfolio isolation, anonymous rejection, and live-fact provenance. It prints
statuses only, never answers or credentials. It does not grade answer semantics;
review generated explanations privately and run retrieval benchmarking separately.


## Local embeddings and existing chat credentials

For a chat-only provider key, set `RAG_EMBEDDING_PROVIDER=local`,
`RAG_CHAT_MODEL=gpt-5-mini`, and `RAG_CHAT_REASONING_EFFORT=minimal`.
The CPU model is `BAAI/bge-small-en-v1.5` (384 dimensions), running with one
ONNX thread and document batches of 16. The image bundles its model cache at
`/app/models`; `RAG_LOCAL_FILES_ONLY=true` prevents model downloads at runtime.
Local development can download/cache the same model in `RAG_LOCAL_MODEL_CACHE`.
CI verifies the bundled model as the unprivileged user with networking disabled.

Collections are namespaced by embedding provider, model and cosine metric.
Changing providers/models creates a distinct collection and requires reindexing;
old persistent collections are retained for operator-managed rollback/cleanup.
Local indexing and retrieval do not initialize the chat client or need an OpenAI
key. Chat uses the Responses API with `store=false`, no conversation memory,
and text-block normalization. This setting does not replace the provider's
contractual data-retention policy.

A local verification on 2026-11-12 indexed the checked-in corpus (54 documents,
674 chunks) and achieved 12/12 positive source hits using the bundled benchmark.
Real chat tests also verified document explanations and exact transient synthetic
facts without modifying the index. This is a scoped snapshot, not a guarantee
for arbitrary or ambiguous questions; the exploratory evaluation remains separate.

### Seeded cold startup

Production measurement on the AVX-only VM found several minutes per 64 chunks
during a full cold rebuild. The image therefore builds a documentation-only
Chroma seed alongside its bundled model. `RAG_INDEX_SEED=/app/index-seed` lets the
index command atomically copy the compatible seed into a new writable volume,
then run its normal incremental check. Existing indexes are preserved. A
provider/model/metric mismatch skips the seed. No live facts or API key are used
to build it. Rebuild the image for documentation/model changes; do not edit its
corpus at runtime. CI verifies offline seed restoration with embedding writes
forbidden, then runs the real source retrieval benchmark as UID 10001.

## v1 reliability gates

The document API now uses **policy-aware evidence retrieval**. A high Chroma
score alone is insufficient: unsupported live-data requests, ambiguous
follow-ups without context, personalized trading recommendations and excerpts
without matching subject matter return the stable insufficient-evidence response.
The same policy is exercised by `investory-rag-eval`, not bypassed by evaluation.

The response validator requires citations to actually retrieved sources. For
document answers, literal numeric claims must appear in excerpts from sources
actually cited by the answer. Uncited retrieved excerpts cannot justify a value.
For Java-authorized financial answers, every numeric claim must equal a
provided aggregate and use the correct currency. Each recognized aggregate label
must belong to the requested, available facts, even when the amount precedes it. This
can reject explanatory prose that names an aggregate outside the requested facts.
Requests for unavailable aggregate values abstain instead of substituting zero. Explicit, same-key
conflicting document assertions abstain and obvious prompt injection in
retrieved excerpts is excluded before generation. No financial facts are
persisted in Chroma.

**Limits:** This is not a semantic entailment proof. It does not catch every
contradiction, spelled-out number, indirect reference, complex derived value,
or malicious instruction. The conservative v1 classifier intentionally rejects
some legitimate requests that need broader data or conversation context.
Keep human-reviewed answer evaluation as a release gate.

Run both benchmark sets against the *same* immutable index:

```sh
investory-rag-eval --cases eval/cases.json --min-hit-rate 0.8
investory-rag-eval --cases eval/exploratory-cases.json --min-abstention-accuracy 1.0
```

The operational startup and recovery checklist is
[docs/operations/rag-v1-recovery.md](../../docs/operations/rag-v1-recovery.md).
CI exercises deterministic local seed/restart scenarios; real pod replacement,
cold image pulls, PostgreSQL incident diagnosis and Argo-managed rollback
require operator-observed evidence and are **not** validated by source CI.

## v1 operational limits and metrics (PR #253)

The Java portfolio RAG route enforces a per-authenticated-principal fixed-window
limit before reading a profile snapshot. It defaults to 10 requests per minute
(`INVESTORY_RAG_MAX_REQUESTS_PER_MINUTE=10`), shared across that user's
portfolios. It returns HTTP 429 when exhausted. This counter is **in-process**
and only suitable for the existing single Java replica; moving to multiple
replicas requires a common store or gateway rate limit. It does not store user
identities in metrics or logs.

The Python service has `RAG_REQUEST_DEADLINE_SECONDS=25`. A request that
exceeds this response deadline receives HTTP 504. Running provider work is
**not forcibly canceled** by the deadline; it keeps its concurrency slot until
it actually finishes. The existing provider call timeout and concurrency limit
remain necessary. Java's outbound read timeout is 35 seconds by default and
must exceed the Python deadline plus local overhead.

`GET /metrics` exposes only fixed-label request counts, outcomes, duration
histograms, and aggregate provider-reported input/output tokens. The endpoint
requires a **separate** `RAG_METRICS_TOKEN` (not `RAG_API_TOKEN`), fails
closed when absent, and is only reachable on the private RAG listener. Neither
request content nor financial amounts, portfolio IDs, usernames, raw source
paths, or authentication credentials appear in metric labels.

For local inspection only, from the trusted pod/network:

```sh
curl -fsS -H "Authorization: Bearer ${RAG_METRICS_TOKEN}" http://127.0.0.1:8090/metrics
```

The companion listens only on pod loopback in GitOps. Prometheus **cannot
directly scrape it from the monitoring namespace**. Do not widen the public
listener or give the monitoring stack the inference bearer token just to
enable scraping. A metrics-only listener/proxy plus dedicated read-only token,
a tightly scoped NetworkPolicy, and a Secret-backed scrape configuration are
required before enabling latency/error/token alert rules. The GitOps
availability alerts (RAG readiness/restarts, Java availability) can use
existing kube-state-metrics immediately without changing that boundary.

Run the Python operational tests with `pytest -q`, the Java rate-limit
tests with the normal Maven unit suite, and verify 429/504/metrics behavior
in a private deployment. Do not mark production latency/error alerts active
until their scrape targets are configured and verified.

### Review hardening after #252/#253

Cold model initialization runs inside the request deadline and admission gate.
Concurrent first requests share one initialized model. Expired or disconnected
requests retain their worker until it actually completes, including work queued
in the thread executor; late provider exceptions are consumed without logging
response bodies.

Fact questions send only the specifically requested aggregates and currency to
the chat model. This prevents another known aggregate from justifying a wrong
answer amount. Field matching uses word boundaries (liquid and illiquid differ)
and recognizes the allowlisted Java field names. Numeric validation recognizes
scientific notation and rejects malformed grouping/decimal-comma ambiguity;
mixed supported currency codes are rejected. Provider token metrics cover both
document and fact calls, including answers later rejected by validation. These
checks remain conservative guards, not semantic entailment proof.

### Verified citation formatting

Live answer evaluation found safe but incorrect abstentions for grouped source
labels, retrieved section tags such as `RET-07`, and literal year notation.
Documentation prompts now use short source IDs, mapped back to the actual
retrieved paths before validation. Grouped citations are canonicalized only if
every component is allowed. A section tag must occur in the excerpt from that
exact source; unknown sources and tags still fail closed. Literal `[n]`/`[n+1]`
notation is preserved verbatim when present in retrieved text, and never counts
as a source citation by itself. Numeric guards remain enforced.

The runtime image retains Bash for the lightweight private HTTP probes used by
GitOps; Docker CI checks its availability. Probes still require `/health` HTTP
200 rather than merely checking that a process exists.

### Java snapshot admission

Live burst acceptance exposed JDBC-pool exhaustion before the per-user budget
could reject incoming requests. Java now admits at most
`INVESTORY_RAG_MAX_CONCURRENT_REQUESTS=1` portfolio RAG operation at a time.
The permit covers both the private snapshot read and the Python call, and is
released on success or any failure. Excess work receives HTTP 429 before any
private snapshot is read. Portfolio authorization remains first, followed by
the per-user request budget and then concurrent admission. Busy authorized
attempts consume their user's request budget.

This is a single-replica guard matching the current single Python worker; raise
capacity only after verifying database and inference capacity. No connection
pool size, financial calculation or portfolio authorization rule was changed.

## Adversarial financial grounding and metrics isolation (PR #257)

The deterministic regression matrix checks invented amounts (including scientific
notation and grouping), mixed currency symbols/codes, missing requested facts,
misassigned field values across net worth/liquid/illiquid/market portfolio,
unsupported source citations, and prompt-injection instructions embedded in
documentation. After generation, each labelled aggregate amount must match the
**specific** Java field rather than merely some value within the supplied facts.
Ambiguous combined claims intentionally abstain. These checks do not replace
semantic evaluation of narrative statements; all real financial computation and
portfolio authorization remain in Java.

For cluster-local Prometheus scraping, set `RAG_METRICS_LISTENER_ENABLED=true`
only when the companion GitOps chart is configured and the separate
`RAG_METRICS_TOKEN` exists. The Python process then starts a second, lightweight,
authenticated HTTP listener on port `RAG_METRICS_LISTENER_PORT=8091` with
**only** `GET /metrics`. It shares the same process counters. The inference
API continues to listen exclusively on `127.0.0.1:8090` inside the pod. The
metrics port fails startup closed if its token is absent or equals the inference
token. The loopback metrics route also rejects reused credentials. The port must have
a NetworkPolicy permitting access only from monitoring and same-namespace
pods. It has no inference endpoint, no public Ingress, and no finance/user
labels.

The companion GitOps PR creates an internal ServiceMonitor and rules for
Python timeouts, provider failures, saturated Python admissions, and aggregate
token usage. Java 429 responses rejected *before* Python cannot be counted
by Python metrics; add Java-specific instrumentation if that gap becomes
operationally significant. Token counters rely on provider-reported usage;
requests rejected without model execution have no token usage.
