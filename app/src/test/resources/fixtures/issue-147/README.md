These expected TSV rows were captured by running
`AccountDailyProjectionBoundaryIT` at the pre-refactor PR base `88dfbb7cb6f930a6c6c403cd3ed520734022d935`.
The same fixed fixture was run at PR #162 head `3cc5e74d5cc5273dfb313f737f40b4fe033e98c0`;
all 36 `account_daily` rows and 36 reconciliation rows were byte-identical. The projection TSV
includes the full persisted financial row, including EUR-converted cash/market/equity, P/L, and
returns. The reconciliation TSV records reported and independently reconstructed values and their
differences. Nonzero fixture reconciliation differences are historical baseline, not zero-drift
assertions between different sources.
