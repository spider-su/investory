# RAG v1 startup and recovery evidence

Scope: correctness and operations only. No new MCP or browser interfaces.

## PostgreSQL connection refusal: incident investigation

The reported one-time Java startup refusal recovered after retry. The cause
is **not established** by source control or passing CI. Do not silently
change JDBC timeouts, PostgreSQL probes, or restart policies without evidence.

Capture **timestamps in UTC** around the actual event:

```sh
kubectl -n investory-prod get pods -o wide
kubectl -n postgres get pods -o wide
kubectl -n investory-prod describe pod <app-pod>
kubectl -n postgres describe pod postgres-0
kubectl -n investory-prod logs <app-pod> --all-containers --timestamps --since=2h
kubectl -n postgres logs postgres-0 --timestamps --since=2h
kubectl -n investory-prod get events --sort-by=.lastTimestamp
kubectl -n postgres get events --sort-by=.lastTimestamp
kubectl -n postgres get endpoints postgres -o wide
```

Review four possible causes independently: PostgreSQL not Ready during app
startup, a DNS/endpoint gap, Ceph PVC attach/recovery delay, or a PostgreSQL
restart/OOM/connection limit. Correlate `Connection refused` with
PostgreSQL readiness, endpoints, restarts, PVC events and database logs.
A one-off refusal with later success is **not** proof of any one cause.
Do not display connection strings or Secret values in shared reports.

## Non-disruptive release gates

Before each change record the source SHA, **scanned** image digest and the
GitOps commit. Verify both workloads and exact image IDs:

```sh
kubectl -n argocd get applications -o wide
kubectl -n investory-prod get deployment,pods -o wide
kubectl -n investory-prod get pods -o jsonpath='{range .items[*]}{.metadata.name}{" "}{range .status.containerStatuses[*]}{.name}{" "}{.imageID}{" "}{.ready}{"\n"}{end}{end}'
kubectl -n investory-prod get events --sort-by=.lastTimestamp
kubectl -n investory-prod rollout status deployment/investory --timeout=10m
```

Confirm the Java readiness endpoint and the private RAG health endpoint from
inside the trusted network. Never route RAG directly through public Ingress.

## Disruptive acceptance tests (operator-approved maintenance window only)

These are **manual gates, not tests performed by CI or this PR**.

1. **Cold pull:** on a node with no cached target digest, schedule the
   workload using the scanned image digest. Verify no ImagePullBackOff,
   readiness on the exact published digest, and a real documentation query.
   Avoid deleting the only known-good image in an emergency.
2. **Pod replacement:** after confirming PostgreSQL health and a rollback
   point, replace the stateless Investory pod using the existing Deployment.
   Observe RAG seed restoration on an empty *disposable* companion volume,
   unchanged production facts, readiness, and cross-portfolio denial.
   Do not delete PostgreSQL or its PVC.
3. **Rollback:** GitOps is authoritative. Revert the GitOps image digest
   or restore the preceding known-good **Git commit** through a reviewed PR;
   do not rely on `kubectl rollout undo` while Argo auto-sync/self-heal is
   active. Wait for Argo Synced/Healthy and check prior image identity,
   exact monetary/currency outputs and negative-question abstention.

For each gate retain timestamps, successful/failed probe counts, startup
duration, image IDs and source revisions. Never capture financial answers,
access tokens or prompt bodies in public CI artifacts.

## What CI can and cannot establish

Python tests exercise seed recovery on empty and existing volumes, model
identity mismatch, and interrupted copies. Docker tests evaluate the immutable
seed offline and assert positive source retrieval and negative abstention.
These validate recovery *mechanisms* but do **not** prove Kubernetes cold pulls,
Ceph attachment, PostgreSQL reachability, or successful real rollback. Those
remain required operator evidence prior to calling this release recovery-ready.

PostgreSQL is a singleton. Confirm independently tested off-cluster backup
restoration before any invasive database change. A Ceph-backed PVC alone is
not independent disaster recovery.
