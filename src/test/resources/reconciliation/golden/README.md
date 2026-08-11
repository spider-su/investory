# Investory golden reconciliation corpus

Small, deterministic regression corpus reduced from anonymized IBKR and XTB scenarios.

The corpus is intentionally not a complete portfolio archive. It exists to preserve previously
observed edge cases while keeping the clean-database verification fast enough to run repeatedly.

Covered cases:

- IBKR direct Treasury `US91282CKB62`: three purchases, percent-of-par pricing, accrued interest,
  coupon, full-call redemption, and the business-date boundary around redemption.
- IBKR `$8,793` deposit business-date regression.
- IBKR cross-currency trade/FX component plus dividend/interest/tax rows.
- XTB VHYD investment-plan/subaccount rebooking that previously produced a false ~6k P/L jump.
- XTB real tracked-account transfer (`51993106 -> 51499241`, 325 USD).
- XTB RESULT_ONLY NATGAS CFD with rollover and swap.
- XTB IKE PLN account with USD-quoted VWRA, PLN-quoted PKN, dividend and withholding tax.
- XTB cash-only funding -> IKE allocation plus interest/tax.

`expected/checkpoints.json` is the semantic contract for future expansion. `GoldenRebuildIT`
implements the high-value assertions directly so a failure produces useful SQL diagnostics.

The golden test must remain offline. Do not add live market/FX calls. Committed broker-derived
fixtures must be reduced, deterministic, anonymized, credential-free, and limited to data needed for
regression semantics.
