# Payment settlement

Settlement consumes persisted canonical obligations and transactions. It never changes a
calculation result.

`PaymentChecker` is deterministic and conservative. It requires compatible currency, uses explicit
type evidence for partial payments, permits one exact unique amount without type text, and returns
`AMBIGUOUS` when multiple exact candidates exist. It does not use fuzzy or AI matching. Exact grosz
comparison is used, matching the old `AccountingObligationReconciliation` behavior.

`PaymentMatch` stores one allocation between an obligation and transaction. An obligation may have
multiple matches, and a transaction may be allocated across obligations while never exceeding its
amount. Automatic matches are `AUTO`; explicit user operations are `MANUAL`. Re-running settlement
is idempotent by the unique obligation/transaction pair.

Canonical bank outflows may be stored as negative transaction amounts. Settlement uses their
absolute payment value and persists a positive `matched_amount`; incoming transactions remain
eligible only when explicit obligation evidence makes them a candidate.

Obligations use `OPEN`, `PARTIALLY_PAID`, `PAID`, `OVERPAID`, and transitional `FROZEN`. Due-date payment is
reported as `PAID`; payment after the due date is `PAID_LATE`. A frozen period cannot be matched,
unmatched, or have its settlement state changed. External bank, eZUS, KSeF, NBP, and filing
verification are outside Stage 5.

The REST `POST /api/profiles/{profileId}/accounting/periods/{month}/settle` operation invokes this
settlement service. Calculation also invokes settlement after obligations are refreshed, so both
`import → calculate` and `calculate → import` flows converge on the same idempotent matches.
