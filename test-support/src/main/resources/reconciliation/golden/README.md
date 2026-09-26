# Investory golden reconciliation corpus

Small, deterministic regression corpus reduced from anonymized IBKR and XTB scenarios.

The corpus is intentionally not a complete portfolio archive. It exists to preserve previously
observed edge cases while keeping the clean-database verification fast enough to run repeatedly.

Covered cases:

- IBKR direct Treasury `US91282CKB62`: three purchases, percent-of-par pricing, accrued interest,
  coupon, full-call redemption, and the business-date boundary around redemption.
- IBKR `$8,793` deposit business-date regression.
- IBKR cross-currency trade/FX component plus dividend/interest/tax rows.
- IBKR C1 source-to-ledger conservation by operation, currency, business date, row count, and
  signed Net Amount.
- XTB VHYD investment-plan/subaccount rebooking that previously produced a false ~6k P/L jump.
- XTB real tracked-account transfer (`91000010 -> 91000002`, 325 USD).
- XTB RESULT_ONLY NATGAS CFD with rollover and swap.
- XTB IKE PLN account with USD-quoted VWRA, PLN-quoted PKN, dividend and withholding tax.
- XTB cash-only funding -> IKE allocation plus interest/tax.

`expected/checkpoints.json` is the semantic contract for the covered cases. Its expected values are
canonical reduced-story facts or values derived from an existing tested accounting/reporting
contract; it is not a place to redefine portfolio metrics. External deposits and withdrawals retain
the domain/reporting flow semantics.
`GoldenRebuildIT`
validates its case IDs and assertion structure, implements the high-value assertions, and emits a
machine-readable `READY` / `NOT_READY` report. A failed check is an `ERROR` and causes Maven to exit
non-zero.

Checkpoint account IDs are Investory's canonical internal IDs: IBKR `91000001`, XTB USD
`91000002`, and XTB PLN `91000003`. Broker fixture filenames and provider-native account values
retain their external IDs.

The golden test must remain offline. Do not add live market/FX calls. Committed broker-derived
fixtures must be reduced, deterministic, anonymized, credential-free, and limited to data needed for
regression semantics.

## Fixture growth rule

Every confirmed accounting or import defect adds the smallest deterministic fixture that reproduces
the defect and one semantic checkpoint. Update `expected/checkpoints.json` and the manifest together.
Do not copy the private broker archive into this corpus; keep private-archive verification as a
separate pre-release check.
