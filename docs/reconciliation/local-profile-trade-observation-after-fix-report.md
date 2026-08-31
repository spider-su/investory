# Reconciliation / materialized-view report

Generated: 2026-08-13T12:05:11.829426300+02:00

Material discrepancy rule: `abs(diff) > max($20, 1% * max(abs(expected), abs(actual)))`. Rows below this rule are summarized as noise and not listed as findings.

## Intended dependency order

1. canonical prices and FX
2. reconstructed position facts
3. reconstructed cash facts
4. account/day reconciliation
5. monthly and portfolio reporting
6. trade-settlement and secondary diagnostics

## 1. Prices and FX

### FX conversion status

```text
conversion_status	count
ESTIMATED	6
```

### FX data-quality issues

```text
(0 rows)
```

### FX consistency issues

```text
issue_code	count
RECIPROCAL_MISMATCH	72
```

### Asset price contract issues

```text
issue_code	count
EXTREME_SAME_DATE_SOURCE_DISAGREEMENT	7
PRICE_CURRENCY_MISMATCH	2633
PRICE_SCALE_MAPPING_MISMATCH	2038
```

## 2. Reconstructed position facts

### Position MV row count

```text
count
42047
```

### Position reconstruction status

```text
reconstruction_status	count
PASS	37263
WARN	4784
```

### Position valuation material mismatches

```text
count	coalesce
295	7518.992518414980000000000000
```

### Position identity issues

```text
(0 rows)
```

### Position currency validation issues

```text
(0 rows)
```

### Duplicate position lots

```text
count
0
```

## 3. Reconstructed cash facts

### Cash MV row count

```text
count
5869
```

### Cash reconstruction status

```text
status	count
PASS	5869
```

### Cash-flow material gaps

```text
count	coalesce
282	10624
```

### Cash-flow incomplete rows

```text
count
0
```

## 4. Account/day reconciliation

### Account/day MV row count

```text
count
5869
```

### Account/day status

```text
status	severity	count
FAIL	INFO	15
FAIL	WARN	207
PASS	INFO	3792
WARN	WARN	1855
```

### Account/day material mismatches

```text
count	coalesce
78	6713
```

### Account statistics vs latest daily

```text
reconciliation_status	count
OK	6
VALUE_MISMATCH	5
```

## 5. Monthly and portfolio reporting

### Account monthly MV row count

```text
count
204
```

### Monthly profit reconciliation

```text
reconciliation_status	count
MISMATCH	40
OK	164
```

### Monthly profit material mismatches

```text
count	coalesce
39	10323
```

### Portfolio service fallback reconciliation

```text
QUERY ERROR: ERROR: canceling statement due to user request
```

### Portfolio validation summary

```text
status	count
FAIL	222
PASS	2309
WARN	1020
```

### Portfolio data quality

```text
total_accounts	reconciled_accounts	unreconciled_accounts	total_open_positions	priced_open_positions	missing_price_count	stale_price_count	proxy_price_count	estimated_price_count	missing_fx_count	ambiguous_cost_basis_currency_count	excluded_position_count	unclassified_cash_operation_count	latest_broker_reconciliation_at	latest_import_at	latest_price_date	latest_fx_date	latest_reporting_refresh_at	quality_state
11	7	4	37	37	0	0	0	0	0	0	0	0	2026-08-12 14:57:40.285393	2026-08-12 14:57:40.285393	2026-08-13	2026-07-31	2026-08-13 11:23:07.940664	CRITICAL
```

## 6. Settlement and secondary diagnostics

### Trade settlement status

```text
reconciliation_status	anomaly_code	count
INCOMPLETE	VALUATION_RECONSTRUCTION_FAILED	159
PASS	OK	2627
REVIEW	MIXED_SETTLEMENT_MODEL	2
REVIEW	RESULT_ONLY_CASH_MISMATCH	9
REVIEW	SALE_VS_CARRYING_VALUE_OUTLIER	6
REVIEW	UNCLASSIFIED_SETTLEMENT_MODEL	1
```

### Realized-result completeness

```text
QUERY ERROR: ERROR: canceling statement due to user request
```

### Non-USD closed-trade anomalies

```text
anomaly_code	count
MISSING_PREVIOUS_POSITION	47
OK	1219
```

### Unsupported transaction states

```text
(0 rows)
```

### Timezone-naive column count

```text
count
1
```

## Material findings for follow-up

### Position valuation rows

```text
count	coalesce
295	7518.992518414980000000000000
```

### Cash-flow rows

```text
count	coalesce
282	10624
```

### Account/day rows

```text
count	coalesce
78	6713
```

### Monthly rows

```text
account_id	month	difference	canonical_profit	expected_boundary_profit
51499241	2025-02-01	10323	-1075.12270970	-11398
51499241	2025-01-01	-9906	618.14659470	10524
51822121	2025-05-01	6560	447.90307100	-6112
51822121	2025-02-01	-6500	-135.41056300	6365
51822121	2025-06-01	-6000	263.70085000	6264
51822121	2025-09-01	5736	1.15617265	-5735
51548444	2025-02-01	5223	11.26154849	-5212
51548444	2025-01-01	-5118	3.20908868	5121
51822121	2025-12-01	4251	-125.78660050	-4376
51822121	2025-03-01	-3755	-165.55644000	3589
51822121	2025-08-01	-3746	1060.98218925	4807
51499241	2025-09-01	3197	892.27484100	-2305
51993106	2026-06-01	3089	-109.27738600	-3198
51822121	2025-07-01	3059	253.75186810	-2805
51993106	2025-06-01	-3000	379.00808500	3379
51499241	2025-08-01	-3000	923.56061100	3924
50290466	2025-02-01	2404	16.11775471	-2388
50290466	2024-12-01	-2202	117.23988640	2319
51993106	2025-09-01	2185	-121.53777575	-2306
51993106	2026-03-01	-1987	-107.34814253	1879
51499241	2025-04-01	-1983	104.01113100	2087
51993106	2026-01-01	-1944	571.90592200	2516
51822121	2025-10-01	1635	-846.36469140	-2481
51822121	2025-04-01	1368	504.98268300	-863
51993106	2026-04-01	1228	644.21672990	-584
51499241	2025-05-01	1163	1025.44747040	-138
51993106	2026-05-01	1056	139.70141300	-916
51993106	2025-05-01	1020	256.15854300	-764
51499241	2025-06-01	1015	771.91848260	-243
51993106	2025-07-01	1000	227.30974900	-773
51499241	2025-10-01	-987	-1710.93705512	-723
51499241	2025-11-01	981	-1350.73208823	-2331
51993106	2025-08-01	-963	539.80228400	1503
51993106	2025-04-01	-815	-166.32432000	649
51993106	2025-11-01	526	323.50879750	-203
51993106	2025-12-01	-490	-6.54575700	483
50290466	2024-11-01	-248	-34.85554426	213
51822121	2025-11-01	93	737.59100690	645
51993106	2026-02-01	90	165.72345663	76
```

### Portfolio fallback rows

```text
QUERY ERROR: ERROR: canceling statement due to user request
```

### Material validation failures

```text
valuation_date	account_id	reconciliation_failures	maximum_market_value_difference	maximum_equity_difference	status
2025-11-03	51499241	1	4244	4244	FAIL
2025-11-04	51499241	1	4068	4068	FAIL
2025-11-04	51822121	1	4068	4068	FAIL
2025-11-19	51499241	1	3682	3682	FAIL
2025-11-20	51499241	1	3376	3376	FAIL
2025-10-22	51822121	1	3249	3249	FAIL
2025-10-25	51822121	1	3249	3249	FAIL
2025-10-23	51822121	1	3249	3249	FAIL
2025-10-24	51822121	1	3249	3249	FAIL
2025-11-23	51499241	1	2973	2973	FAIL
2025-11-21	51499241	1	2973	2973	FAIL
2025-11-22	51499241	1	2973	2973	FAIL
2025-11-18	51499241	1	2948	2948	FAIL
2025-11-24	51499241	1	2906	2906	FAIL
2025-10-16	51993106	1	2787	2787	FAIL
2025-11-25	51499241	1	2364	2364	FAIL
2025-11-05	51499241	1	2171	2171	FAIL
2025-11-03	51822121	1	2122	2122	FAIL
2025-11-08	51499241	1	2074	2074	FAIL
2025-11-11	51499241	1	2074	2074	FAIL
2025-11-10	51499241	1	2074	2074	FAIL
2025-11-09	51499241	1	2074	2074	FAIL
2025-11-06	51499241	1	2074	2074	FAIL
2025-11-07	51499241	1	2074	2074	FAIL
2025-10-25	51993106	1	2060	2060	FAIL
2025-10-24	51993106	1	2060	2060	FAIL
2025-10-26	51993106	1	2060	2060	FAIL
2025-10-15	51993106	1	2034	2034	FAIL
2025-10-20	51993106	1	1603	1603	FAIL
2025-10-23	51993106	1	1498	1498	FAIL
2025-10-21	51993106	1	1497	1497	FAIL
2025-10-22	51993106	1	1468	1468	FAIL
2025-10-15	51822121	1	1154	1154	FAIL
2025-10-13	51822121	1	1154	1154	FAIL
2025-10-20	51822121	1	1154	1154	FAIL
2025-10-17	51822121	1	1154	1154	FAIL
2025-10-14	51822121	1	1154	1154	FAIL
2025-10-19	51822121	1	1154	1154	FAIL
2025-10-18	51822121	1	1154	1154	FAIL
2025-10-16	51822121	1	1154	1154	FAIL
2025-02-26	51499241	1	1101	1101	FAIL
2025-10-21	51822121	1	1086	1086	FAIL
2025-10-13	51499241	1	888	888	FAIL
2025-10-28	51993106	1	825	825	FAIL
2025-10-18	51993106	1	800	800	FAIL
2025-10-17	51993106	1	800	800	FAIL
2025-10-19	51993106	1	800	800	FAIL
2025-10-27	51993106	1	761	761	FAIL
2025-02-28	51499241	1	746	746	FAIL
2025-10-10	51499241	1	745	745	FAIL
```

