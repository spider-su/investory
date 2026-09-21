# Stage 2 calculation rules

These rules describe the pure 2026 calculation layer. They accept normalized PLN values and do not
classify source documents or fetch exchange rates.

## Ryczałt

`RyczaltRules2026` is `RYCZALT_2026_POC_V1`. Revenue is supplied by numeric rate bucket. Buckets
are processed in numeric rate order, so map insertion order cannot change the result. Paid social
deduction plus 50% of paid health contribution is available after already-consumed deductions.
Available deduction is applied proportionally to rate buckets, with the final bucket receiving the
deterministic remainder. Unused deduction is returned as carry-forward. Taxable bases and tax are
rounded to whole PLN.

## VAT

`VatRules2026` is `VAT_2026_POC_V1`. Output VAT is the normalized output amount plus signed sales
corrections. Payable VAT is the rounded output plus explicit adjustments less rounded deductible
input VAT, floored at zero. Settlement components are rounded to whole PLN using HALF_UP.

## ZUS

`ZusRules2026` is `ZUS_2026_POC_V1`. An active JDG without qualifying UoP pays full JDG social
contribution and optionally voluntary sickness. Qualifying UoP pays no JDG social in this layer but
still uses the applicable health band. An inactive JDG pays zero. Health bands use the normalized
revenue after paid social: low through 60,000 PLN, medium through 300,000 PLN, then high.

## Rounding policy

| Operation | Scale | Mode |
| --- | ---: | --- |
| FX amount | 2 | HALF_UP |
| ZUS contribution | 2 | HALF_UP |
| health deduction | 2 | HALF_UP |
| deduction allocation | 2 | HALF_UP |
| ryczałt taxable base and tax | 0 | HALF_UP |
| VAT settlement | 0 | HALF_UP |

The names are intentional: callers should use the semantic operation rather than treating all
numbers as display formatting.
