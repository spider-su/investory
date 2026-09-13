# Accounting post-freeze polishing

This list is deliberately outside the freeze-hardening scope. Items move back
into implementation only when verification shows a correctness or isolation
defect:

- split large Accounting services and repositories;
- rename `accounting_poc_*` and `accounting_tmp_*` objects;
- add uniform audit columns, CHECK constraints, pagination, filtering, and
  optimistic locking;
- normalize money and temporal types and remove duplicate filing views/DTO
  metadata;
- improve OpenAPI enum and `/api/v1` naming consistency;
- replace signed document IDs, simplify provenance, and remove dead tables;
- add query-plan-backed indexes and cosmetic UI cleanup;
- extend database isolation beyond the minimum profile correctness boundary.

All entries are post-freeze polish unless a concrete production defect is
demonstrated.
