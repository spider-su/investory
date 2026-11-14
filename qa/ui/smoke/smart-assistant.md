# Smart Assistant — portfolio dashboard smoke

## Scope

Route: `/portfolios/{portfolioId}/dashboard`. The feature is a lightweight,
stateless display of independent questions. It uses the existing authenticated portfolio endpoint
`POST /api/v1/portfolios/{portfolioId}/rag/ask` and the new, equivalently
scoped documentation route `POST /api/v1/portfolios/{portfolioId}/rag/ask-docs`. It does not store
conversations, infer permissions, or add new financial calculations.

## Preconditions

- RAG Java/Python feature enabled and Ready on the test environment.
- One read-authorized portfolio and one other inaccessible portfolio for
  the active test user; separate anonymous session.
- Synthetic fixture with known exact net worth and liquid asset values.
- Test with light and dark theme and a phone-sized viewport.

## Checks

1. Dashboard displays the **Smart Assistant** button. Press or activate with
   keyboard; panel opens, focus goes to the question input and aria-expanded
   becomes true. Closing restores trigger focus; Escape also closes.
2. Ask `What is my net worth?`; verify exact expected amount and currency,
   `live:profile` source label and response text shown inside the panel.
   Neither the question nor response appears as rendered HTML.
3. Change question type to **How Investory works**, ask how transfers affect
   performance, and verify a documentation source label. Inspect the Java adapter:
   documentation requests send only `question`, with **no** financial snapshot.
   Confirm a user without portfolio membership is denied before the Python call.
4. Switch back to **My portfolio** and ask a second *independent* supported aggregate question; verify both
   messages remain visible for the current page. Do not claim contextual
   follow-up memory or long-lived conversation history.
5. Ask a question lacking supported evidence: result explicitly abstains
   instead of fabricating values. Check incorrect currencies and mismatched
   aggregate values using synthetic fixtures at the backend boundary.
6. Generate one slow request. Send remains disabled until completion; close
   the panel and confirm the browser cancels its outstanding fetch.
7. Induce a synthetic 429, 502, 503, or 504. The UI shows a friendly error
   and permits another question. No stack traces, provider details or tokens.
8. Authenticated user requesting a different portfolio gets 403; anonymous
   user gets 401/redirect as configured. The UI does not permit replacing
   the server-bound portfolio ID through its controls.
9. Inspect browser network: same-origin POST, matching CSRF header, no
   cross-origin API calls or embedded OpenAI credentials. No assistant content
   in localStorage/sessionStorage.
10. Refresh the page: prior messages disappear (expected for v1).
11. Confirm narrow viewport layout and keyboard accessibility.

## Release gate

CI: `node --test adapters/web-ui/src/test/js/smart-assistant.test.cjs`
plus the Java `SmartAssistantTemplateContractTest`, Maven unit/integration
tests and browser suite. The production deployment retains the standard
`latest` image + pod replacement approach; verify runtime identity and
Java/RAG readiness after the new image is published.
