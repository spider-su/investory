# Reconciliation evidence

This directory contains local-profile reconciliation reports and investigation notes produced while
validating specific defects or freeze-readiness questions. These files are evidence snapshots, not the
canonical reconciliation contract.

Use [`../quality/reconciliation.md`](../quality/reconciliation.md) for current C0-C7 semantics,
status rules, tolerances, and supported tooling. Use [`../domain/portfolio-accounting.md`](../domain/portfolio-accounting.md),
[`../domain/asset-identity-and-money.md`](../domain/asset-identity-and-money.md), and
[`../domain/fx-normalization.md`](../domain/fx-normalization.md) for the financial contracts being
validated.

A local report is authoritative only for the data, code revision, and investigation described in that
file. Do not infer current application health from an older PASS, FAIL, REVIEW, or discrepancy count.
When a report establishes a durable rule, move that rule into the matching canonical domain or quality
document and keep the report only as supporting evidence.

`local-profile-db-persistence-freeze-readiness.md` is the current named freeze-readiness audit referenced
from the documentation index. Other files should normally be opened only when investigating the defect
or historical state named in their filename.
