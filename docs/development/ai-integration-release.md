# MCP and RAG release plan

Each implementation stage is a separate commit. Release evidence must identify
the exact source revision and image digest.

1. **Plan:** record this sequence and its acceptance gates.
2. **Acceptance tooling:** provide a bounded HTTP smoke check for authenticated
   MCP initialization/discovery/invocation and Java portfolio RAG responses.
   Emit status and source labels only, never financial answers or credentials.
   Keep deterministic tests separate from real OpenAI evaluation.
3. **Image publication:** publish and scan the standalone RAG image only after
   main Java and Python CI pass. Bundle versioned documentation and evaluation
   inputs with the image so deployment can reproduce the tested corpus.
4. **Private deployment:** update the Investory chart in `ops-autopilot` with an
   opt-in loopback RAG companion and bounded resources. Keep Python inaccessible
   through the public Service/Ingress. Inject credentials from a Secret; do not
   put them in values, command arguments, logs, or Git. Index before serving.
5. **Real acceptance:** with a configured OpenAI key, index the checked-in corpus,
   run retrieval evaluation (source hit rate at least 0.8), then run the authenticated
   smoke check against the deployed Java endpoint. Confirm unauthorized portfolio
   access is rejected. Financial facts remain transient and unindexed.
6. **Release:** wait for PR CI; merge to develop, then release to main through a
   PR. Wait for main CI and image scanning/publication before restarting the
   workload. Verify runtime image digests, readiness, Argo sync/health, and HTTP
   health. Activate RAG only after credentials and real acceptance are available.

Missing credentials block real inference and RAG activation, not source builds
or release of the disabled-by-default integration. Do not report those gates as
passed or enable a deployment which cannot complete its indexing step. An active
index must be rebuilt on corpus/model changes; this initial companion rebuilds
its disposable index on pod recreation from the immutable image corpus.

MCP retains its three Investment read tools. RAG uses the authenticated Java
portfolio endpoint; a new MCP RAG tool and browser chat interface are outside
this release plan.


## Local-embedding activation (2026-11-12)

The operator selected local embeddings because the existing provider key grants
chat access but rejects embedding models. Use the bundled CPU model and the
existing `gpt-5-mini` chat access. The key must be copied from the approved local
configuration into the encrypted RAG Secret without printing or committing its
plaintext. Release the local-model image first, then activate the GitOps companion
and run retrieval evaluation plus scoped Java/MCP/RAG smoke checks. Preserve the
already-working Java deployment if companion startup fails.

Cold production indexing proved too slow on the VM without AVX2. The companion
was disabled through GitOps recovery while the image gained a public-documents
index seed. Re-activation requires offline seed/no-reembedding CI, fresh scanned
publication receipts, and the same deployed inference/authorization gates.
