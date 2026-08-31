# Integrations, Jobs & Notifications — Manual QA Plan

## Scope

Integration settings UI/API, provider configuration, scheduled jobs, market/FX data, Yahoo export, OpenAI analysis, Telegram and notifications. UI route: `/settings/integrations`; API root: `/api/v1/admin/integrations`.

## Before testing

- Use non-production credentials and a dedicated test chat/channel. Never expose API keys, tokens or decrypted secrets in tickets/log extracts.
- Integration settings are an administrator-only surface (`/settings/integrations` and `/api/v1/admin/integrations`). The private-base-URL guard is a deployment safeguard for that trusted surface, not a complete SSRF defense; if less-trusted users can reach it, replace the host check with resolved-address validation covering IPv4 and IPv6 ranges before enabling outbound tests.
- Record deployment version, namespaces, relevant pods, ConfigMaps/Secrets references (names only) and existing integration/job state.
- Ensure a safe mechanism exists to restore the prior enabled state after testing.

## Test cases

| ID | UI/API action | Expected result | DB / Kubernetes evidence |
| --- | --- | --- | --- |
| INT-01 | Open integration settings; inspect every registered plugin by type. | Metadata, fields, enabled state and job controls are correctly grouped; unavailable plugins are explained rather than broken. | `integration_instances`, `integration_jobs`; pod logs show plugin discovery without duplicate registrations. |
| INT-02 | Save a valid configuration, reload settings, then submit missing/invalid fields. | Valid settings persist; validation names the failing field; secrets are masked on every read. | `integration_instances`, `integration_secrets`; verify ciphertext/metadata only—never print secret values. |
| INT-03 | Use the connection test for each configured provider; test a controlled bad credential/endpoint. | Result distinguishes success, validation failure and provider/network failure; bad test does not overwrite a working configuration. | API response, structured logs/correlation ID; DB state unchanged unless explicitly saved. |
| INT-04 | Disable a plugin with scheduled work, observe one due interval, then enable it. | Disabled provider does not execute/emit side effects; re-enable resumes normal scheduling without duplicate concurrent run. | Job scheduler logs, `integration_jobs`, pod events/restarts; provider-specific tables only change after enabled execution. |
| INT-05 | Trigger/observe market price and FX refresh using known mapped and unmapped assets. | Valid quotes/rates retain provider/date provenance; unsupported mappings are skipped/visible; provider failure preserves last known-good inputs. | `asset_price_history`, `exchange_rates`, mapping rows; logs show job start/end/outcome. |
| INT-06 | Run Yahoo export twice with unchanged data, then after a source change. | Export is stable/idempotent for unchanged state and advances appropriately after real change. | `yahoo_export_state`, generated payload/logs; compare source reporting values. |
| INT-07 | Trigger a notification-worthy condition (e.g., stale import, drawdown/concentration in safe fixture). | One correctly formatted event is persisted and delivered to test channel; repeat evaluation does not spam duplicates. | `notification_event`, `drawdown_alert_state`, delivery logs; verify redaction of sensitive data. |
| INT-08 | Test Telegram command routing in dedicated test chat, including unknown/malformed command. | Authorized expected command returns scoped portfolio information; malformed/unknown command is safe and explanatory; no secrets leak. | Telegram/integration logs and notification delivery state; inspect authorization/config state. |
| INT-09 | Invoke OpenAI portfolio analysis only with approved test data and a known disabled/missing configuration case. | Enabled path reports controlled result; disabled/missing config fails clearly with no retry storm or secret exposure. | Scheduler/application logs, relevant integration state, pod resource use and outbound-error handling. |
| INT-10 | Restart a pod during/around a scheduled job and inspect health. | Configuration survives restart, jobs do not overlap unexpectedly, health exposes stale import/provider state accurately. | Deployment rollout, pod events, restart count, readiness/liveness, job and app logs. |

## Release exit

- Every enabled production integration has a recorded connection test and one observed successful scheduled/manual run.
- For the settings browser flow, verify the complete sequence on a safe test provider: enter a draft endpoint and use **Test connection** (the saved diagnostic must not change), submit **Save changes**, test again, **Enable integration**, then save an enabled **Automatic refresh** schedule. Confirm the page reflects the saved provider result and job state after each redirect.
- Disabled integrations produce no external side effect.
- Settings and logs preserve secret masking; no crash loop, repeated failure storm or duplicate notification was observed.
