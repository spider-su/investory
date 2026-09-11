# AI QA reports

Save one Markdown report per completed mission. Use the report shape below so results remain easy to
review and compare. Keep secrets, session data, and unnecessary personal data out of reports.

```markdown
# AI QA report: <mission>

- Result: `PASS` / `SUSPICIOUS` / `FAIL`
- Environment / profile:
- Mission revision:
- Browser / viewport:
- Started / finished:

## Summary

## Checks

| Check | Result | Observation / evidence |
| --- | --- | --- |

## Console and network

- Page errors:
- Console errors/warnings:
- Failed or unexpected first-party requests:

## Evidence

- [Screenshot or trace](evidence/<file>)

## Skipped, blocked, or unexecuted

## Follow-up
```

FAIL needs concrete evidence. A browser, deployment, or authentication blocker is reported as
blocked/environment evidence, not silently converted to an application failure.
