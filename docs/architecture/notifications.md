# Notification events

Investory notifications are durable application events, not direct Telegram calls from business
modules. Investment and Retirement create channel-neutral `NotificationCandidate` facts through the
small publisher contract in `shared`. `integrations` owns persistence, formatting, retry state, and
external delivery. Telegram is one optional adapter.

## Lifecycle and transaction boundary

```text
domain/application state change
  -> candidate with deterministic fingerprint
  -> INSERT notification_event ... ON CONFLICT DO NOTHING
  -> PENDING
  -> PROCESSING (short database lease, unique worker token)
  -> formatter selected by event type
  -> delivery adapter confirms send
  -> DELIVERED

delivery failure -> RETRYABLE -> bounded delayed retry -> EXHAUSTED
worker crash -> expired PROCESSING lease -> claimable retry
```

Import failure/partial candidates are inserted in the same `REQUIRES_NEW` transaction that finalizes
the import row. Retirement sustainability candidates are inserted in the explicit reviewed-revision
transaction. A rollback therefore removes both the state change and its candidate. System audit runs
are created by a committed database function during asynchronous post-import follow-up; their
notification insertion is a separate best-effort transaction. Failure is logged with the audit ID
and can be replayed. Telegram availability never affects candidate creation or domain commits.

Delivery is at-least-once. An event is marked `DELIVERED` only after every configured adapter returns
successfully. A process failure after Telegram accepted a message but before the database commit can
produce a duplicate delivery; Telegram has no transactional acknowledgement protocol with the local
database. The stable fingerprint prevents duplicate event rows, not this narrow delivery ambiguity.
Configured channels are currently one delivery unit: if channel A succeeds and channel B fails, the
event is retried and A may receive the message again. Per-channel delivery state is intentionally
deferred until a second real channel requires it.

## POC scope and deferred work

The current POC is effectively single-channel (Telegram). The event-level at-least-once contract is
intentional. Email delivery, notification UI, recovery notifications, richer user preferences, and
per-channel delivery tracking are deferred until a real second-channel requirement exists.

## Identity and state

The unique `fingerprint` is producer-owned and must identify one economic event boundary:

| Event | Fingerprint |
| --- | --- |
| System audit error | `SYSTEM_AUDIT_ERROR:{auditRunId}` |
| Failed/partial import | `IMPORT_FAILED_OR_PARTIAL:{importHistoryId}:{finalStatus}` |
| Plan became unsustainable | `PLAN_BECAME_UNSUSTAINABLE:{planId}:{revisionId}` |
| Daily digest | `DAILY_DIGEST:{Europe/Warsaw local date}` |
| Threshold alert | `ALERT:{ruleCode}:{SHA-256 of normalized message}` |

`PENDING` has not been attempted. `PROCESSING` is held by one worker token until its short lease
expires. `RETRYABLE` failed below the configured attempt limit and has a future `next_attempt_at`.
An expired processing lease is claimable, so a crashed worker cannot strand an event. `EXHAUSTED`
reached the attempt limit and requires operator replay. `DELIVERED` has a confirmation time. Attempts
and concise errors are retained; stack traces, secrets, raw import rows, and imported payloads are not
event payload fields.

The retirement event is created only by an explicit review/rebaseline and only for a canonical Base
transition from sustainable to unsustainable. Opening Analysis or recalculating frozen inputs does
not publish. A sustainable revision is still observable as the previous reviewed state for a later
transition, but recovery delivery is deferred.

## Adding an event

1. Add its stable code and database check value.
2. Define the application state-change boundary and fingerprint before adding presentation.
3. Publish a small structured payload inside the owning transaction. Do not include adapter types.
4. Add one `NotificationMessageFormatter` implementation with an actionable deep link.
5. Add producer, idempotency, rollback, formatter, and delivery tests.

P1/P2 rules should use the same outbox. Threshold rules must publish only on a persisted state
transition and should model cooldown/recovery explicitly rather than emitting every scheduled run.

## Configuration

| Property | Meaning | Default |
| --- | --- | --- |
| `app.notifications.base-url` | Public Investory origin used for deep links | `http://localhost:8080` |
| `app.notifications.dispatch.interval-ms` | Dispatcher scheduling delay | `60000` |
| `app.notifications.dispatch.max-attempts` | Bounded delivery attempts | `5` |
| `app.notifications.dispatch.retry-delay-minutes` | Linear retry-delay unit | `15` |
| `app.telegram.enabled` | Creates the Telegram adapter and bot | `false` |

Scheduled digest and threshold rules publish candidates into the same outbox. Digest fingerprints
identify the intended local calendar day. Threshold alert output is deterministically ordered and its
fingerprint includes a SHA-256 of the normalized message, so an unchanged condition is suppressed by
the unique outbox fingerprint while a changed condition creates a new event. All delivery is performed
by the dispatcher.
