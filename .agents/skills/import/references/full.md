# Full import analysis

Trace the failing or suspicious import through the current Investory pipeline.

Use the repository implementation as the source of truth. Prefer concrete log,
database, and source-file evidence over assumptions.

Trace, where applicable:

source file
-> parser/importer
-> normalized records
-> database writes
-> FX/price enrichment
-> derived-data refresh
-> portfolio projection
-> reconciliation refresh
-> import readiness/status

For each failed stage:

1. identify the first meaningful failure
2. distinguish root cause from downstream symptoms
3. identify affected account, file, symbol, date, or currency when available
4. show concrete evidence
5. classify the cause

Use one of:

- `SOURCE_FILE`
- `PARSER`
- `DATA_IMPORT`
- `DATABASE`
- `FX`
- `PRICE_HISTORY`
- `PROJECTION`
- `RECONCILIATION`
- `READINESS`
- `UNKNOWN`

Do not hide a real projection/reconciliation failure merely because broker rows were
successfully applied.

Do not implement fixes.

## Output

# Full Import Analysis

Status: `PASS | WARN | FAIL | NOT VERIFIED`

## Pipeline

| Stage | Status | Evidence |
|---|---|---|

## Root Cause

Report:

- failing stage
- affected file/account/symbol/date/currency
- primary error
- root cause
- downstream symptoms
- recommended fix

## Data Applied

State explicitly whether broker data was:

- not applied
- partially applied
- fully applied but pipeline not ready
- fully applied and ready

## Confidence

`HIGH | MEDIUM | LOW`

State what was actually inspected.

## Recommendation

Output exactly one:

- `IMPORT HEALTHY - RUN $recon`
- `FIX IMPORT ROOT CAUSE`
- `FIX DERIVED PIPELINE`
- `INSUFFICIENT DATA`
