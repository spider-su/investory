# ZUS DRA export status

The native Ryczalt module now exposes a read-only unsigned ZUS DRA KEDU draft at
`GET /api/profiles/{profileId}/accounting/periods/{yyyy-MM}/zus-dra`. It uses the completed native
period, the persisted ZUS amount, and taxpayer identity from `ryczalt_profile`.

This is a draft export only. It is not a signed submission, authority confirmation, or eZUS client.

The active ZUS implementation is the native calculation and payment-detection code in
`modules/ryczalt`. Its current external-verification capability remains intentionally unavailable.
See [`modules/ryczalt/README.md`](../../modules/ryczalt/README.md) and
[`docs/domain/accounting-poc.md`](../domain/accounting-poc.md) for current scope.

Keep these concerns separate:

- calculation facts and filing projection;
- local validation and versioned KEDU rendering;
- user-owned signing/submission;
- confirmation polling and durable authority evidence.

The current export intentionally does not claim schema validation or acceptance. Do not add a mock
result that could be mistaken for an accepted ZUS submission. If signing/submission is added later,
add versioned KEDU fixtures and schema resources with that separate implementation.
