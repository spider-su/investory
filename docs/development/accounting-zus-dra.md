# ZUS DRA export status

No ZUS DRA renderer, validator, submission client, or KEDU fixture exists in the current source
tree. This document is retained as a design note only; it is not an implemented runtime boundary
and must not be used as evidence that Investory can generate, submit, or confirm a ZUS filing.

The active ZUS implementation is the native calculation and payment-detection code in
`modules/ryczalt`. Its current external-verification capability remains intentionally unavailable.
See [`modules/ryczalt/README.md`](../../modules/ryczalt/README.md) and
[`docs/domain/accounting-poc.md`](../domain/accounting-poc.md) for current scope.

If DRA export is implemented later, keep these concerns separate:

- calculation facts and filing projection;
- local validation and versioned KEDU rendering;
- user-owned signing/submission;
- confirmation polling and durable authority evidence.

Add versioned fixtures and schema resources with the implementation. Do not add a mock result that
could be mistaken for an accepted ZUS submission.
