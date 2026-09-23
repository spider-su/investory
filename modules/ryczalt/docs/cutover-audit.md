# Ryczalt cutover audit — historical

This document is retained only as historical migration evidence.

The Accounting-to-Ryczalt cutover is complete in the active runtime:

- `modules/ryczalt` is the active accounting implementation.
- Active Web and REST paths use native Ryczalt contracts.
- Legacy Accounting controllers, bridges, and mobile accounting routes are removed.
- Bank CSV import, KSeF sync, invoice recognition/approval, counterparties/rules, native month inputs,
  RYCZALT/VAT/ZUS calculation, obligations, settlement, freeze/reopen, and payment history are native.
- Normal runtime accounting reads and writes use `ryczalt_*` tables.
- Historical `accounting_*` Flyway migrations/reference fixtures may remain until a separate data
  retention/database cleanup is performed.
- The physical `modules/accounting` source tree, when present, is retired reference code and is not
  part of the Maven runtime reactor.

For the current architecture and supported API use:

- see `../README.md`;
- see `rest-api.md`;
- see `lifecycle.md`;
- see `settlement.md`;
- see `KT.md`.

Git history is the authoritative source for the detailed staged cutover audit.
